```
hover::append (a: u32, base: usize, mut ref res: mut [mut c8])-> usize
```

Append the digits of `a` at the end of `res`, using the base `base`,
the letters of the digits above 9 in lower case.

The digits are written straight at the end of `res`.

<hr>

**Info:** no intermediate slice is allocated

**Parameters:**

- `a`: the value to write
- `base`: the base of the digits
- `res`: the slice the digits are appended to,
  grown as needed

**Returns:** the number of digits written, either:

- 1 for 0
- the length of `a` in base `base`, without:
  - its sign
  - its prefix

**Compile-time assertion:** `(base <= 36)`

**Example:**

```
let dmut res = copy "0x"s8;
append (255u32, 16us, ref res);

assert(res == "0xff");
```

Laid out as:

```text
0x | ff
   | digits
```