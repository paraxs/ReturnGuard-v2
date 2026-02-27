#!/usr/bin/env python3
"""
Phase 2 matrix summarizer.

Usage:
  python tools/phase2_matrix_summary.py
  python tools/phase2_matrix_summary.py --matrix docs/smoke-test-matrix-v0.1.0-beta.md
"""

from __future__ import annotations

import argparse
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import List


@dataclass
class Row:
    row_id: str
    product: str
    merchant: str
    date: str
    price: str
    confidence_overall: int | None
    confidence_price: int | None
    save_guard_triggered: str
    notes: str


def parse_table_rows(content: str) -> List[Row]:
    rows: List[Row] = []
    for line in content.splitlines():
        if not line.startswith("|"):
            continue
        if "| ID |" in line or "|---" in line:
            continue
        cols = [part.strip() for part in line.strip().strip("|").split("|")]
        if len(cols) != 11:
            continue
        row_id = cols[0]
        if not row_id.isdigit():
            continue
        if all(not c for c in cols[1:]):
            continue
        rows.append(
            Row(
                row_id=row_id,
                product=cols[3],
                merchant=cols[4],
                date=cols[5],
                price=cols[6],
                confidence_overall=parse_int(cols[7]),
                confidence_price=parse_int(cols[8]),
                save_guard_triggered=cols[9],
                notes=cols[10],
            ),
        )
    return rows


def parse_int(raw: str) -> int | None:
    raw = raw.strip()
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        return None


def is_ok(value: str) -> bool:
    v = value.strip().lower()
    return v in {"ok", "yes", "true", "pass", "correct"}


def top_note_tokens(rows: List[Row], n: int = 5) -> List[tuple[str, int]]:
    tokens = Counter()
    for row in rows:
        note = row.notes.lower()
        for token in re.findall(r"[a-z][a-z0-9_-]{3,}", note):
            if token in {"after", "with", "from", "this", "that", "sample", "control"}:
                continue
            tokens[token] += 1
    return tokens.most_common(n)


def mean(values: List[int]) -> float:
    return sum(values) / len(values) if values else 0.0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--matrix",
        default="docs/smoke-test-matrix-v0.1.0-beta.md",
        help="Path to the markdown matrix file.",
    )
    args = parser.parse_args()

    matrix_path = Path(args.matrix)
    if not matrix_path.exists():
        print(f"Matrix not found: {matrix_path}")
        return 1

    content = matrix_path.read_text(encoding="utf-8")
    rows = parse_table_rows(content)
    if not rows:
        print("No executed rows found in matrix.")
        return 0

    total = len(rows)
    product_ok = sum(1 for r in rows if is_ok(r.product))
    merchant_ok = sum(1 for r in rows if is_ok(r.merchant))
    date_ok = sum(1 for r in rows if is_ok(r.date))
    price_ok = sum(1 for r in rows if is_ok(r.price))
    guards = sum(1 for r in rows if r.save_guard_triggered.strip().lower() in {"yes", "true", "1"})

    overall_values = [r.confidence_overall for r in rows if r.confidence_overall is not None]
    price_values = [r.confidence_price for r in rows if r.confidence_price is not None]

    print("Phase 2 Matrix Summary")
    print(f"- Executed rows: {total}")
    print(f"- Product accuracy:  {product_ok}/{total} ({product_ok * 100.0 / total:.1f}%)")
    print(f"- Merchant accuracy: {merchant_ok}/{total} ({merchant_ok * 100.0 / total:.1f}%)")
    print(f"- Date accuracy:     {date_ok}/{total} ({date_ok * 100.0 / total:.1f}%)")
    print(f"- Price accuracy:    {price_ok}/{total} ({price_ok * 100.0 / total:.1f}%)")
    print(f"- Save guard triggered: {guards}/{total}")
    if overall_values:
        print(f"- Mean overall confidence: {mean(overall_values):.1f}")
    if price_values:
        print(f"- Mean price confidence:   {mean(price_values):.1f}")

    tokens = top_note_tokens(rows)
    if tokens:
        print("- Frequent note tokens:")
        for token, count in tokens:
            print(f"  - {token}: {count}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
