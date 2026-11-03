/** Render the actual component markup/styles in a separate viewport for responsive assertions. */
export function reviewLayout(element: HTMLElement, width: number) {
  const frame = document.createElement('iframe');
  frame.style.cssText = `position:fixed;left:-10000px;width:${width}px;height:720px;border:0`;
  document.body.appendChild(frame);
  const doc = frame.contentDocument!;
  for (const style of document.head.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
  doc.body.style.cssText = 'margin:0;--tbm-border:#dce3ed;--tbm-primary:#2455a5;--tbm-muted:#68758c;--tbm-text:#18243b';
  const root = element.cloneNode(true) as HTMLElement;
  doc.body.appendChild(root);
  return { root, window: frame.contentWindow!, remove: () => frame.remove() };
}
