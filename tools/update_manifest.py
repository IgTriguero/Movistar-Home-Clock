#!/usr/bin/env python3
"""Genera el update.json de una release de HomeClock.

Lee el changelog Markdown de docs/release-notes/<version>.md, lo convierte en
texto plano para el diálogo de actualización de la app y escribe el manifiesto
que consume UpdateClient.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def markdown_to_notes(text: str) -> str:
    """Convierte el changelog Markdown en texto legible dentro de un AlertDialog."""
    # La sección de instalación no aporta nada en el diálogo de la app.
    text = re.split(r"(?m)^##\s+Instalación\s*$", text, flags=re.IGNORECASE)[0]
    # Quita bloques de código, enlaces, encabezados y énfasis.
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"(?m)^#{1,6}\s*", "", text)
    text = text.replace("**", "").replace("__", "").replace("`", "")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", required=True, type=int)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--notes-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    if not args.notes_file.is_file():
        raise SystemExit(f"Falta el changelog: {args.notes_file}")

    notes = markdown_to_notes(args.notes_file.read_text(encoding="utf-8"))
    if not notes:
        raise SystemExit(f"El changelog está vacío: {args.notes_file}")

    data = {
        "versionCode": args.code,
        "versionName": args.version,
        "apkUrl": (
            f"https://github.com/{args.repo}/releases/download/"
            f"{args.ref}/{args.apk}"
        ),
        "sha256": args.sha,
        "notes": notes,
    }
    args.output.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
