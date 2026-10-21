/**
 * Reads a number the way the language says.
 *
 * Bun 1.4.3 for Android answers `NaN` when the radix of `parseInt` is left
 * out. `parseInt("127")` is `127` on a desktop and `NaN` on a phone, while
 * `parseInt("127", 10)` is `127` in both. The engine and the server use
 * `parseInt` in a few places without a radix, and the values they read come
 * out wrong on a phone.
 *
 * This module gives the radix back. It follows the rule of the language:
 * without a radix, a number that starts with `0x` is read in base sixteen,
 * and every other number in base ten.
 *
 * The module is imported first by the entry of the engine, so the rule is in
 * place before any other module runs.
 */
const original = globalThis.parseInt;

const patched = function parseInt(value, radix) {
  if (radix !== undefined) return original(value, radix);
  const text = String(value).trim();
  return original(value, /^[+-]?0[xX]/.test(text) ? 16 : 10);
};

globalThis.parseInt = patched;
// `Number.parseInt` is the same function under another name, and the code
// of the engine uses both.
Number.parseInt = patched;

export default patched;
