// The "API docs by Redocly" link at the foot of Redoc's sidebar, whose logo the page loads from cdn.redoc.ly once it
// hydrates. Redoc has no option for it, so `removeRedoclyLogo` cuts the element out of Redoc's code: build.mjs makes
// it conditional in the library that renders on the server, and renderPage removes it from the script it inlines.
// Both builds (redoc.js and redoc.standalone.js) contain the element once, as the first child of the sidebar footer:
//   jsx(Wrapper,{$collapsedSidebar:n,children:jsx(`a`,{..., href:`https://redocly.com/redoc/`, ...,
//     children:n?jsx(Logo,{}):jsxs(Fragment,{children:[jsx(`p`,{children:`API docs by`}),jsx(Logo,{full:!0})]})})})
// (`jsx(` is `(0,V.jsx)(` in the standalone build).

const JSX = String.raw`(?:jsx|\(0,[\w$]+\.jsx\))`;

const REDOCLY_LOGO = new RegExp(
  String.raw`${JSX}\([\w$]+,\{\$collapsedSidebar:[\w$]+,children:${JSX}\(` +
    '`a`,\\{target:`_blank`,rel:`noopener noreferrer`,href:`https://redocly\\.com/redoc/`' +
    String.raw`[\s\S]*?\{full:!0\}\)\]\}\)\}\)\}\)`,
  'g',
);

/**
 * Replaces the element in `source` (one of Redoc's bundles) with what `replace` returns for its code.
 * @throws when the element is not found exactly once, e.g. after a Redoc upgrade changed it
 */
export function replaceRedoclyLogo(source, replace, file = 'the Redoc bundle') {
  const found = source.match(REDOCLY_LOGO)?.length ?? 0;
  if (found !== 1) throw new Error(`Expected the Redocly logo link once in ${file}, found it ${found} times`);
  return source.replace(REDOCLY_LOGO, (element) => replace(element));
}

/** `source` without the element. */
export function removeRedoclyLogo(source, file) {
  return replaceRedoclyLogo(source, () => 'null', file);
}
