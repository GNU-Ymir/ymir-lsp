"""
The Ymir support of gdb: the frames named by their demangled Ymir function,
the frames of the runtime under the main function of the program elided, the
temporaries of the compiler left out of the locals, and the slices, options,
maps and objects of Ymir printed as such.

Loaded by `gdb -x ymir.py`, or `source ymir.py` in gdb.
"""

import re

import gdb
import gdb.printing
from gdb.FrameDecorator import FrameDecorator

_NAME = re.compile(r"[A-Za-z0-9_]")
_INT_SIZES = ("8", "16", "32", "64", "128", "size")
_SCALARS = (gdb.TYPE_CODE_INT, gdb.TYPE_CODE_FLT, gdb.TYPE_CODE_BOOL, gdb.TYPE_CODE_CHAR, gdb.TYPE_CODE_ENUM)


class _Demangler:
    """
    The readings of a symbol mangled by gyc. A length does not always start
    where a path does, so every rule yields each way it reads the text from a
    position, as a `(text, end)` pair, the symbol keeping the reading that
    consumes it whole.
    """

    def __init__(self, symbol):
        self.s = symbol
        self.memo = {}

    def symbol(self):
        s = self.s
        if not s.startswith("_Y"):
            return None
        if s.endswith("MI"):
            for t in self.exact(self.type, 2, len(s) - 2):
                return t
        for path, i in self.path(2, len(s)):
            for kind in ("F", "MT", "CT", "DT"):
                if s.startswith(kind, i):
                    for params, j in self.params(i + len(kind), len(s)):
                        if s.startswith("Z", j) and (j + 1 == len(s) or self.exact(self.type, j + 1, len(s))):
                            shown = params if kind == "F" else params[1:]
                            return "%s(%s)" % (path, ", ".join(shown))
            if s[i:] in ("T", "TP", "V", "TI", "MI") or re.fullmatch(r"VT\d*", s[i:]):
                return path
        return None

    def rule(fn):
        def cached(self, i, end):
            key = (fn.__name__, i, end)
            if key not in self.memo:
                self.memo[key] = []
                readings = {}
                for text, j in fn(self, i, end):
                    readings.setdefault(j, text)
                self.memo[key] = [(text, j) for j, text in readings.items()]
            return self.memo[key]
        return cached

    def exact(self, rule, i, end):
        return [text for text, j in rule(i, end) if j == end]

    def numbers(self, i, end):
        j = i
        while j < end and self.s[j].isdigit():
            j += 1
        if self.s.startswith("0", i):
            j = min(j, i + 1)
        for k in range(j, i, -1):
            yield int(self.s[i:k]), k

    def sized(self, i, end):
        """The `(start, end)` of each length-prefixed content at `i`."""
        for n, k in self.numbers(i, end):
            if k + n <= end:
                yield k, k + n

    @rule
    def path(self, i, end):
        for part, j in self.part(i, end):
            for rest, k in self.path(j, end):
                yield part + "::" + rest, k
            yield part, j

    @rule
    def part(self, i, end):
        for k, e in self.sized(i, end):
            if k == e or not all(_NAME.match(c) for c in self.s[k:e]):
                continue
            for templates, j in self.templates(e, end):
                yield self.s[k:e] + ("{%s}" % ", ".join(templates) if templates else ""), j

    @rule
    def templates(self, i, end):
        yield (), i
        for first, j in self.template(i, end):
            for rest, k in self.templates(j, end):
                yield (first,) + rest, k

    @rule
    def template(self, i, end):
        s = self.s
        if s.startswith("N", i):
            yield from self.value(i + 1, end)
            for mut, k in self.mutability(i + 1):
                for t, j in self.type(k, end):
                    yield mut + t, j
        if s.startswith("L", i):
            for n, k in self.numbers(i + 1, end):
                if s.startswith("N", k):
                    for mut, m in self.mutability(k + 1):
                        if m + n <= end:
                            for types in self.exact(self.list_types, m, m + n):
                                yield mut + "(" + ", ".join(types) + ")", m + n

    def mutability(self, i):
        if self.s.startswith("dm", i):
            yield "dmut ", i + 2
        elif self.s.startswith("m", i):
            yield "mut ", i + 1
        yield "", i

    @rule
    def list_types(self, i, end):
        yield (), i
        if self.s.startswith("N", i):
            for t, j in self.type(i + 1, end):
                for rest, k in self.list_types(j, end):
                    yield (t,) + rest, k

    @rule
    def value(self, i, end):
        s = self.s
        for prefix in ("i", "f", "c"):
            if s.startswith(prefix, i):
                for n, k in self.numbers(i + 1, end):
                    if s.startswith("_", k) and k + 1 + n <= end:
                        v = s[k + 1:k + 1 + n]
                        yield ("'%s'" % v if prefix == "c" else v), k + 1 + n
        if s.startswith("b0", i) or s.startswith("b1", i):
            yield ("true" if s[i + 1] == "1" else "false"), i + 2
        if s.startswith("s", i):
            for n, k in self.numbers(i + 1, end):
                if n <= 10:
                    texts = [(bytes(data).decode("utf-8", "replace"), e) for data, e in self.decimals(k, end, n)]
                    texts.sort(key=lambda t: not t[0].isprintable() or "�" in t[0])
                    for text, e in texts:
                        yield _quote(text), e
                if k + n <= end:
                    yield '"..."', k + n
        if s.startswith("t", i):
            for k, e in self.sized(i + 1, end):
                for values in self.exact(self.values, k, e):
                    yield "(" + ", ".join(values) + ")", e
        if s.startswith("TP", i):
            for n, k in self.numbers(i + 2, end):
                if s.startswith("_", k) and k + 1 + n <= end:
                    yield demangle(s[k + 1:k + 1 + n]), k + 1 + n

    def decimals(self, i, end, n):
        """The `n` bytes of a string written in decimal from `i`, the short strings being mangled byte by byte."""
        if n == 0:
            yield (), i
            return
        for b, k in self.numbers(i, min(end, i + 3)):
            if b < 256:
                for rest, j in self.decimals(k, end, n - 1):
                    yield (b,) + rest, j

    @rule
    def values(self, i, end):
        yield (), i
        for k, e in self.sized(i, end):
            for v in self.exact(self.value, k, e):
                for rest, j in self.values(e, end):
                    yield (v,) + rest, j

    @rule
    def params(self, i, end):
        yield (), i
        for p, j in self.param(i, end):
            for rest, k in self.params(j, end):
                yield (p,) + rest, k

    @rule
    def param(self, i, end):
        for prefix, shown in (("R", "ref "), ("L", "lazy ")):
            if self.s.startswith(prefix, i):
                for t, j in self.type(i + 1, end):
                    yield shown + t, j
        yield from self.type(i, end)

    @rule
    def type(self, i, end):
        s = self.s
        if s.startswith("x", i):
            for t, j in self.type(i + 1, end):
                yield "mut " + t, j
        if s.startswith("b", i):
            yield "bool", i + 1
        if s.startswith("v", i):
            yield "void", i + 1
        for prefix, sizes in (("c", ("8", "16", "32")), ("f", ("16", "32", "64", "80", "128", "size")), ("i", _INT_SIZES), ("u", _INT_SIZES)):
            for size in sizes:
                if s.startswith(prefix + size, i):
                    yield prefix + size, i + 1 + len(size)
        if s.startswith("A", i):
            for n, k in self.numbers(i + 1, end):
                if s.startswith("_", k):
                    for m, e in self.sized(k + 1, end):
                        for t in self.exact(self.type, m, e):
                            yield "[%s; %d]" % (t, n), e
        for prefix, form in (("P", "&%s"), ("R", "..%s"), ("S", "[%s]"), ("O", "(%s)?"), ("FUT", "future(%s)"), ("YLD", "yield(%s)")):
            if s.startswith(prefix, i):
                for k, e in self.sized(i + len(prefix), end):
                    for t in self.exact(self.type, k, e):
                        yield form % t, e
                    if prefix == "P":
                        start = k + 1 if s.startswith("x", k) else k
                        for p in self.exact(self.path, start, e):
                            yield p, e
        if s.startswith("T", i):
            for k, e in self.sized(i + 1, end):
                for types in self.exact(self.tuple_types, k, e):
                    yield "(" + ", ".join(types) + ")", e
        if s.startswith("MP", i):
            for k, e in self.sized(i + 2, end):
                for key, j in self.type(k, e):
                    if s.startswith("_", j):
                        for value in self.exact(self.type, j + 1, e):
                            yield "[%s => %s]" % (key, value), e
        for prefix, shown in (("DG", "dg"), ("FP", "fn")):
            if s.startswith(prefix, i):
                for params, j in self.params(i + len(prefix), end):
                    if s.startswith("Z", j):
                        for ret, k in self.type(j + 1, end):
                            yield "%s(%s)-> %s" % (shown, ", ".join(params), ret), k
        for k, e in self.sized(i, end):
            for p in self.exact(self.path, k, e):
                yield p, e
        yield from self.path(i, end)

    @rule
    def tuple_types(self, i, end):
        yield (), i
        for k, e in self.sized(i, end):
            for t in self.exact(self.type, k, e):
                for rest, j in self.tuple_types(e, end):
                    yield (t,) + rest, j


def _demangle_path(symbol):
    """The path at the head of `symbol`, its parameters elided, as the runtime prints it."""
    parts = []
    i = 2
    while True:
        m = re.match(r"\d+", symbol[i:])
        if m is None:
            break
        n = int(m.group())
        start = i + len(m.group())
        if n == 0 or start + n > len(symbol):
            break
        parts.append(symbol[start:start + n])
        i = start + n
    return "::".join(parts) + ("(...)" if i < len(symbol) and symbol[i] in "FMCT" else "") if parts else None


def demangle(symbol):
    """The Ymir name of the symbol `symbol` mangled by gyc, `symbol` itself if it is not one."""
    if not symbol.startswith("_Y"):
        return symbol
    base, dot, clone = symbol.partition(".")
    try:
        name = _Demangler(base).symbol()
    except RecursionError:
        name = None
    name = name or _demangle_path(base)
    if name is None:
        return symbol
    return name + (" [clone .%s]" % clone if dot else "")


_RUNTIME = re.compile(r"_yrt_|__libc_start|_start$|main$|start_thread$|clone3?$")
_TEMPORARY = re.compile(r"YI_\d+$|__\w+_\d+$")


def _is_runtime(frame):
    name = frame.function()
    return isinstance(name, str) and _RUNTIME.match(name) is not None


class YmirFrame(FrameDecorator):
    """A frame named by its demangled function, without the temporaries of the compiler."""

    def __init__(self, base, elided=None):
        super().__init__(base)
        self._elided = elided

    def function(self):
        name = super().function()
        return demangle(name) if isinstance(name, str) else name

    def elided(self):
        return self._elided

    def frame_locals(self):
        variables = super().frame_locals()
        if variables is None:
            return None
        return [v for v in variables if not _TEMPORARY.match(str(v.symbol()))]


class YmirFrameFilter:
    """Decorates the frames as `YmirFrame`, eliding the frames of the runtime under the outermost Ymir one."""

    def __init__(self):
        self.name = "ymir"
        self.priority = 100
        self.enabled = True

    def filter(self, frames):
        last = None
        runtime = []
        for frame in frames:
            if _is_runtime(frame):
                runtime.append(frame)
                continue
            if last is not None:
                yield YmirFrame(last)
            yield from (YmirFrame(f) for f in runtime)
            runtime = []
            last = frame
        if last is not None:
            yield YmirFrame(last, [YmirFrame(f) for f in runtime] or None)
        else:
            yield from (YmirFrame(f) for f in runtime)


_Printer = getattr(gdb, "ValuePrinter", object)


def _guarded(to_string):
    """`to_string`, `<inaccessible>` when the memory it reads is not, a failing printer failing the whole scope."""
    def guarded(self):
        try:
            return to_string(self)
        except gdb.error:
            return "<inaccessible>"
    return guarded


def _limit():
    limit = gdb.parameter("print elements")
    return limit if isinstance(limit, int) and limit > 0 else None


def _text(ptr, length, size):
    """The characters of a Ymir string, at most `print elements` of them, `None` when they are not text."""
    limit = _limit()
    shown = length if limit is None else min(length, limit)
    data = gdb.selected_inferior().read_memory(ptr, shown * size).tobytes() if shown else b""
    try:
        text = data.decode("utf-8" if size == 1 else "utf-32-le")
    except UnicodeDecodeError as e:
        if shown == length or e.start < len(data) - 3:
            return None
        text = data[:e.start].decode("utf-8")
    if any(ord(c) < 32 and c not in "\n\t\r" for c in text):
        return None
    return _quote(text) + ("..." if shown < length else "")


def _quote(text):
    escaped = text.replace("\\", "\\\\").replace('"', '\\"')
    return '"' + escaped.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r") + '"'


def _class_name(vtable):
    """The demangled class of the vtable `vtable` points into."""
    m = re.search(r"<(_Y\w+)(\+\d+)?>", gdb.format_address(vtable))
    return demangle(m.group(1)) if m else None


class ElementsPrinter(_Printer):
    """
    A slice, `[T]`, or an array, `[T; N]`: a string when its elements are
    characters, its elements otherwise, at most `print elements` of them.
    """

    def __init__(self, val, length, ptr):
        self._val = val
        self._len = length
        self._ptr = ptr
        self._text = None
        self._readable = True
        element = val.type.strip_typedefs().target() if ptr is None else ptr.type.target()
        element = element.strip_typedefs()
        if ptr is not None and length > 0:
            try:
                gdb.selected_inferior().read_memory(int(ptr), element.sizeof)
            except gdb.MemoryError:
                self._readable = False
        unnamed = element.name == "__unknown__" and element.code == gdb.TYPE_CODE_INT and element.sizeof == 1
        if self._readable and ptr is not None and (element.name in ("c8", "c32") or unnamed):
            try:
                self._text = _text(int(ptr), length, element.sizeof)
            except gdb.MemoryError:
                pass

    @_guarded
    def to_string(self):
        if self._text is not None:
            return self._text
        return "len %d" % self._len if self._readable else "len %d <inaccessible>" % self._len

    def display_hint(self):
        return None if self._text is not None else "array"

    def num_children(self):
        if self._text is not None or not self._readable:
            return 0
        limit = _limit()
        return self._len if limit is None else min(self._len, limit)

    def child(self, i):
        return "[%d]" % i, (self._ptr + i).dereference() if self._ptr is not None else self._val[i]

    def children(self):
        for i in range(self.num_children()):
            yield self.child(i)


def _slice(val):
    return ElementsPrinter(val, int(val["len"]), val["ptr"])


def _array(val):
    low, high = val.type.strip_typedefs().range()
    first = val[low].address if high >= low else None
    return ElementsPrinter(val, high - low + 1, first)


class OptionPrinter(_Printer):
    """An option, `(T)?`: its value, or its error."""

    def __init__(self, val):
        content = val["content"]
        if int(val["hasValue"]) != 0:
            self._state, self._inner = "Ok", content["value"]
        elif int(content["error"]) != 0:
            self._state, self._inner = "Err", content["error"]
        else:
            self._state, self._inner = "none", None
        self._scalar = self._inner is not None and self._inner.type.strip_typedefs().code in _SCALARS

    @_guarded
    def to_string(self):
        return "%s(%s)" % (self._state, self._inner.format_string()) if self._scalar else self._state

    def num_children(self):
        return 0 if self._inner is None or self._scalar else 1

    def child(self, i):
        return ("value" if self._state == "Ok" else "error"), self._inner

    def children(self):
        for i in range(self.num_children()):
            yield self.child(i)


class ClassPrinter(_Printer):
    """An object, named by its dynamic class, with its fields."""

    HIDDEN = ("#_vtable", "#_monitor")

    def __init__(self, val):
        pointer = val.type.strip_typedefs().code == gdb.TYPE_CODE_PTR
        self._null = pointer and int(val) == 0
        self._obj = val.dereference() if pointer else val
        self._vtable = None
        if not self._null:
            try:
                self._vtable = int(self._obj["#_vtable"])
            except gdb.MemoryError:
                pass
        self._fields = [] if self._vtable is None else [
            f.name for f in self._obj.type.strip_typedefs().fields() if f.name not in self.HIDDEN and not f.artificial
        ]

    @_guarded
    def to_string(self):
        if self._null:
            return "null"
        if self._vtable is None:
            return "<inaccessible>"
        return _class_name(self._vtable) or "object"

    def num_children(self):
        return len(self._fields)

    def child(self, i):
        return self._fields[i], self._obj[self._fields[i]]

    def children(self):
        for i in range(self.num_children()):
            yield self.child(i)


class MapPrinter(_Printer):
    """A map, `[K => V]`, by its number of entries: their types are not in the debug information."""

    def __init__(self, val):
        self._val = val

    @_guarded
    def to_string(self):
        if int(self._val) == 0:
            return "null"
        return "len %d" % int(self._val.dereference()["len"])


def _is_ymir(t):
    """Whether `t` is a type of gyc, which names its scalars `__unknown__` and its aggregates `tuple_<n>`."""
    return t.name is not None and (t.name == "__unknown__" or re.match(r"tuple_\d+$", t.name) is not None)


def _fields(t):
    return tuple(f.name for f in t.fields()) if t.code == gdb.TYPE_CODE_STRUCT else ()


def _lookup(val):
    t = val.type.strip_typedefs()
    if t.code == gdb.TYPE_CODE_PTR:
        target = t.target().strip_typedefs()
        fields = _fields(target)
        if fields[:2] == ClassPrinter.HIDDEN:
            return ClassPrinter(val)
        if fields[:5] == ("minfo", "entries", "len", "cap", "loaded"):
            return MapPrinter(val)
        return None
    if t.code == gdb.TYPE_CODE_ARRAY and _is_ymir(t.target().strip_typedefs()):
        return _array(val)
    fields = _fields(t)
    if fields == ("len", "ptr", "blk_info"):
        return _slice(val)
    if fields == ("hasValue", "content"):
        return OptionPrinter(val)
    if fields[:2] == ClassPrinter.HIDDEN:
        return ClassPrinter(val)
    return None


class YmirPrinters:
    """The printers of the Ymir values."""

    def __init__(self):
        self.name = "ymir"
        self.enabled = True
        self.subprinters = None

    def __call__(self, val):
        return _lookup(val) if self.enabled else None


def register(objfile=None):
    """Registers the frame filter and the printers, for every objfile when `objfile` is `None`."""
    target = objfile if objfile is not None else gdb
    target.frame_filters["ymir"] = YmirFrameFilter()
    gdb.printing.register_pretty_printer(objfile, YmirPrinters(), replace=True)


register()
