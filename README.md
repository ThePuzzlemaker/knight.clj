# knight.clj

WIP (bad) implementation of [sampersand][samp]'s [Knight][kn] in Clojure.

[samp]: https://github.com/sampersand
[kn]: https://github.com/knight-lang/knight-lang

## Implementation-specific behaviour

- All UB (as far as I can tell) is caught, usually in the form of an exception.
- Strings allow unicode
- `PROMPT` will return an empty string if EOF is returned
- `RANDOM` will return a random 32-bit integer from 0 to 2147483647 (inclusive, afaik)
- `` ` `` has a few changes:
  - stderr is put into the variable `sh_stderr`
  - stdout is returned, except when the exit code is nonzero, in that case:
  - stdout is put into the variable `sh_stdout` and the the exit code is returned
- `LENGTH` is the number of bytes, when encoded as UTF-8
- `DUMP` will print `Block(opaque)` for blocks
- `ASCII` supports Unicode codepoints, e.g. `O A 128563` => 😁, `O A "😁"` => 128563
- `?` will never consider `BLOCK`s equal.
- `=` will coerce its first argument to a string if it is not an identifier, and use that dynamically as a variable name.
- `VALUE` is implemented
- `~` is implemented
- `USE` is implemented
- Extension: `XGENSYM(string)`, similarly to the Lisp operation of the same name (minus `X`), generates a fresh name that is not currently defined. This has very low likelihood of collision, but in that case it will try up to 1,000 times to create a new name before giving up (using 6-digit random numbers, so the likelihood of 1,000 consequitive collisions is astronomically (un)lucky and if that happens perhaps run and get a lottery ticket as fast as possible). Example: `O D XGENSYM "foo"` => `String(foo658237)`, `O D XGENSYM "bar"` => `String(bar430567)`. Coerces argument to string, returns string.
## License

Copyright (c) 2021 ThePuzzlemaker

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

