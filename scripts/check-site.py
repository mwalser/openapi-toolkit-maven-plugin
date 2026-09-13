#!/usr/bin/env python3
"""Check generated goal pages and local HTML links before publishing the Maven site."""

from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


class Page(HTMLParser):
    def __init__(self, content):
        super().__init__(convert_charrefs=True)
        self.anchors = set()
        self.links = []
        self.text = []
        self.feed(content)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.anchors.add(attrs["id"])
        if tag == "a" and "name" in attrs:
            self.anchors.add(attrs["name"])
        for name in ("href", "src"):
            if attrs.get(name):
                self.links.append(attrs[name])

    def handle_data(self, data):
        self.text.append(data)


def main():
    root = Path("target/site").resolve()
    required = ["index.html", "usage.html", "performance.html", "limitations.html",
                "development.html", "license.html", "plugin-info.html"]
    descriptor = Path("target/classes/META-INF/maven/plugin.xml")
    if not descriptor.is_file():
        sys.exit("Missing plugin descriptor. Run mvn site first.")
    mojos = ET.parse(descriptor).findall("./mojos/mojo")
    required.extend(f"{mojo.findtext('goal')}-mojo.html" for mojo in mojos)
    errors = [f"Missing page: {name}" for name in required if not (root / name).is_file()]
    pages = {file: Page(file.read_text(encoding="utf-8")) for file in root.rglob("*.html")}

    for file, page in pages.items():
        for link in page.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            target = (file.parent / unquote(url.path)).resolve() if url.path else file
            if not target.is_relative_to(root):
                errors.append(f"{file.name}: link escapes the site: {link}")
                continue
            if target.is_dir():
                target /= "index.html"
            if not target.is_file():
                errors.append(f"{file.name}: missing link target: {link}")
            elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].anchors:
                errors.append(f"{file.name}: missing anchor: {link}")

    # The descriptor is the source of truth: confirm that each user property reached the HTML.
    for mojo in mojos:
        file = root / f"{mojo.findtext('goal')}-mojo.html"
        if file not in pages:
            continue
        content = "".join(pages[file].text)
        for parameter in mojo.findall("./configuration/*"):
            expression = parameter.text or ""
            if expression.startswith("${openapi.toolkit.") and expression[2:-1] not in content:
                errors.append(f"{file.name}: missing user property {expression}")

    for name in ("index.html", "performance.html"):
        page = pages.get(root / name)
        if page and "${project." in "".join(page.text):
            errors.append(f"{name}: unexpanded Maven version template")

    if errors:
        sys.exit("\n".join(errors))
    print(f"Checked {len(pages)} HTML pages, local links, and {len(mojos)} generated goals.")


if __name__ == "__main__":
    main()
