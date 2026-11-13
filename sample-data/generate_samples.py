#!/usr/bin/env python3
"""
Generates the SYNTHETIC demonstration datasets for LandStack (stdlib only, deterministic seed).
No real persons, parcels or registrations are represented. Run:  python3 generate_samples.py
"""
import csv
import random
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).resolve().parent
rng = random.Random(20261113)

FIRST = ["Asha", "Ravi", "Meena", "Suresh", "Kavita", "Arjun", "Pooja", "Vikram", "Neha", "Rakesh", "Sunita", "Manoj",
         "Priya", "Deepak", "Anita", "Sanjay", "Rekha", "Amit", "Geeta", "Rahul"]
LAST = ["Devi", "Kumar", "Singh", "Prasad", "Yadav", "Sharma", "Mishra", "Gupta", "Verma", "Paswan", "Mahto", "Jha"]
BIHAR = {"Patna": ["Phulwari", "Danapur", "Masaurhi"], "Gaya": ["Bodh Gaya", "Sherghati"], "Nalanda": ["Rajgir", "Hilsa"]}
VILLAGES = ["Rampur", "Sitapur", "Chandpur", "Bhagwanpur", "Madhopur", "Lakhanpur", "Shivpur", "Kishanpur"]


def name():
    return f"{rng.choice(FIRST)} {rng.choice(LAST)}"


def bihar_csv():
    """Heterogeneous CSV with deliberate quality problems for the demo."""
    header = ["ULPIN", "Khasra No", "Owner Name", "Area", "Unit", "Land Type", "Village", "Tehsil", "District", "State",
              "Registration No", "Registration Date", "Latitude", "Longitude"]
    rows = []
    for i in range(1, 37):
        district = rng.choice(list(BIHAR))
        lat = round(rng.uniform(24.6, 26.0), 6)
        lon = round(rng.uniform(84.6, 85.9), 6)
        unit, area = rng.choice([("acre", round(rng.uniform(0.2, 4), 2)), ("bigha", round(rng.uniform(0.5, 6), 2)),
                                 ("katha", rng.randint(2, 18)), ("hectare", round(rng.uniform(0.1, 1.5), 3)),
                                 ("dhur", rng.randint(5, 19)), ("sq ft", rng.randint(900, 9000))])
        rows.append([f"BR{rng.randint(10, 38):02d}{i:010d}", f"{rng.randint(1, 999):04d}", name(), area, unit,
                     rng.choice(["Agricultural", "Krishi", "Abadi", "Residential", "Banjar", "Commercial", "Orchard", "Talab"]),
                     rng.choice(VILLAGES), rng.choice(BIHAR[district]), district, "Bihar", f"REG/{2015 + i % 9}/{1000 + i}",
                     f"{rng.randint(13, 28):02d}-{rng.randint(1, 12):02d}-20{rng.randint(10, 24)}", lat, lon])
    # deliberate problems (documented in sample-data/README.md)
    rows[3][0] = ""; rows[3][1] = ""                         # missing identifiers -> blocked (ID_MISSING)
    rows[5][3] = -2.5                                         # negative area -> blocked (AREA_NEGATIVE)
    rows[7][12] = 95.12                                       # latitude out of range -> not mappable
    rows[9][12] = ""; rows[9][13] = ""                        # no coordinates -> "Not mappable"
    rows[11][13] = ""                                         # one coordinate only (COORD_PARTIAL)
    rows[13][4] = ""                                          # unit missing (UNIT_MISSING)
    rows[15][11] = "04/05/2019"                               # ambiguous date (DATE_AMBIGUOUS unless day-first)
    rows[17][2] = "N/A"; rows[17][5] = "-"                    # placeholders -> null, not invented
    rows[19][2] = '=HYPERLINK("http://example.invalid","click")'  # CSV-injection probe (escaped on export)
    rows[21][3] = "12 acres"; rows[21][4] = ""                # unit embedded in area value
    rows[23][4] = "kanal"                                     # regional unit with no Bihar factor -> UNIT_NOT_CONVERTIBLE
    rows[25][3] = "250000"; rows[25][4] = "hectare"           # implausibly large (AREA_EXTREME)
    rows.append(list(rows[2]))                                # identical repeated row (DUP_IDENTICAL_ROW)
    dup = list(rows[0]); dup[3] = float(dup[3]) * 3 if isinstance(dup[3], (int, float)) else dup[3]
    rows.append(dup)                                          # same ULPIN+locality, different area (DUP_ROW_IN_FILE)
    with open(OUT / "bihar_parcels_sample.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(rows)
    # A later "revised" extract that overlaps with the first file -> cross-import duplicate candidates
    revised = [list(r) for r in rows[:5]]
    for r in revised:
        if isinstance(r[3], (int, float)):
            r[3] = round(float(r[3]) * 1.2, 2)
    with open(OUT / "bihar_parcels_revised.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(revised)


def col(n):
    s = ""
    n += 1
    while n:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def sheet_xml(rows):
    out = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
           '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>']
    for ri, row in enumerate(rows, start=1):
        out.append(f'<row r="{ri}">')
        for ci, v in enumerate(row):
            ref = f"{col(ci)}{ri}"
            if v is None or v == "":
                continue
            if isinstance(v, (int, float)) and not isinstance(v, bool):
                out.append(f'<c r="{ref}"><v>{v}</v></c>')
            else:
                out.append(f'<c r="{ref}" t="inlineStr"><is><t xml:space="preserve">{escape(str(v))}</t></is></c>')
        out.append("</row>")
    out.append("</sheetData></worksheet>")
    return "".join(out)


def write_xlsx(path, sheets):
    ct = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
          '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">',
          '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>',
          '<Default Extension="xml" ContentType="application/xml"/>',
          '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>',
          '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>']
    wb = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
          '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
          'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>']
    rels = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">']
    for i, (name, _) in enumerate(sheets, start=1):
        ct.append(f'<Override PartName="/xl/worksheets/sheet{i}.xml" '
                  'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>')
        wb.append(f'<sheet name="{escape(name)}" sheetId="{i}" r:id="rId{i}"/>')
        rels.append(f'<Relationship Id="rId{i}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
                    f'Target="worksheets/sheet{i}.xml"/>')
    n = len(sheets) + 1
    rels.append(f'<Relationship Id="rId{n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>')
    ct.append("</Types>"); wb.append("</sheets></workbook>"); rels.append("</Relationships>")
    styles = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
              '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
              '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>'
              '<fills count="1"><fill><patternFill patternType="none"/></fill></fills>'
              '<borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs>'
              '<cellXfs count="1"><xf xfId="0"/></cellXfs></styleSheet>')
    root_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                 '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
                 'Target="xl/workbook.xml"/></Relationships>')
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", "".join(ct))
        z.writestr("_rels/.rels", root_rels)
        z.writestr("xl/workbook.xml", "".join(wb))
        z.writestr("xl/_rels/workbook.xml.rels", "".join(rels))
        z.writestr("xl/styles.xml", styles)
        for i, (_, rows) in enumerate(sheets, start=1):
            z.writestr(f"xl/worksheets/sheet{i}.xml", sheet_xml(rows))


def rajasthan_xlsx():
    """Different source vocabulary: Khatedar, Rakba, Gram, Zila, pucca/kaccha bigha, Survey No as text."""
    header = ["Khasra/Survey No", "Khatedar", "Rakba", "Unit", "Bhumi Prakar", "Gram", "Tahsil", "Zila", "State", "Lat", "Long"]
    rows = [header]
    districts = {"Jaipur": ["Sanganer", "Amer"], "Ajmer": ["Kishangarh", "Beawar"], "Jodhpur": ["Osian", "Bilara"]}
    for i in range(1, 21):
        d = rng.choice(list(districts))
        unit = rng.choice(["pucca bigha", "kaccha bigha", "hectare", "bigha"])
        rows.append([f"{rng.randint(1, 450):03d}/{rng.randint(1, 9)}", name(), round(rng.uniform(0.5, 8), 2), unit,
                     rng.choice(["Kheti", "Charagah", "Abadi", "Banjar", "Gochar"]), rng.choice(VILLAGES), rng.choice(districts[d]),
                     d, "Rajasthan", round(rng.uniform(25.5, 27.5), 6) if i % 6 else "", round(rng.uniform(73.0, 76.5), 6) if i % 6 else ""])
    readme = [["Synthetic sample workbook for LandStack. Not real data."], ["Select the 'Khasra Register' sheet when importing."]]
    write_xlsx(OUT / "rajasthan_khasra_register.xlsx", [("ReadMe", readme), ("Khasra Register", rows)])


def pdf_bytes(pages):
    """Minimal text-based PDF (Helvetica), one string list per page; [] gives an image-like empty page."""
    objs = []
    def add(b):
        objs.append(b)
        return len(objs)
    font = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    page_ids, content_ids = [], []
    for lines in pages:
        ops = ["BT", "/F1 11 Tf", "14 TL", "72 760 Td"]
        for ln in lines:
            esc = ln.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            ops.append(f"({esc}) Tj T*")
        ops.append("ET")
        stream = "\n".join(ops).encode("latin-1") if lines else b""
        content_ids.append(add(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream"))
        page_ids.append(None)
    pages_id = len(objs) + len(pages) + 1
    for i, cid in enumerate(content_ids):
        page_ids[i] = add(f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 {font} 0 R >> >> /Contents {cid} 0 R >>".encode())
    kids = " ".join(f"{p} 0 R" for p in page_ids)
    assert add(f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode()) == pages_id
    catalog = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode())
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, b in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + b + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    for o in offsets:
        out += f"{o:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objs) + 1} /Root {catalog} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def pdfs():
    page1 = ["SYNTHETIC SAMPLE - NOT AN OFFICIAL RECORD", "Record of Rights extract (demonstration)", "",
             "ULPIN: BR2100000101", "Khasra No: 0457", "Owner Name: Kavita Mishra", "Area: 1.25", "Unit: acre",
             "Land Type: Agricultural", "Village: Madhopur", "Tehsil: Danapur", "District: Patna", "State: Bihar",
             "Latitude: 25.612345", "Longitude: 85.051234"]
    page2 = ["ULPIN: BR2100000102", "Khasra No: 0458", "Owner Name: Deepak Jha", "Area: 3", "Unit: katha",
             "Land Type: Abadi", "Village: Madhopur", "Tehsil: Danapur", "District: Patna", "State: Bihar"]
    (OUT / "mutation_extract_sample.pdf").write_bytes(pdf_bytes([page1, page2]))
    (OUT / "scanned_like_sample.pdf").write_bytes(pdf_bytes([[], []]))


def perf_csv():
    header = ["Parcel ID", "Survey No", "Owner", "Area", "Unit", "Land Use", "Village", "Tehsil", "District", "State", "Latitude", "Longitude"]
    with open(OUT / "performance_5000.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        for i in range(1, 5001):
            d = rng.choice(list(BIHAR))
            mappable = i % 5 != 0
            w.writerow([f"PERF{i:08d}", f"{i % 997:04d}", name(), round(rng.uniform(0.1, 6), 3),
                        rng.choice(["acre", "hectare", "bigha", "katha"]), rng.choice(["Agricultural", "Residential", "Banjar"]),
                        f"{rng.choice(VILLAGES)} {i % 40}", rng.choice(BIHAR[d]), d, "Bihar",
                        round(rng.uniform(24.6, 26.0), 6) if mappable else "", round(rng.uniform(84.6, 85.9), 6) if mappable else ""])


def invalid_files():
    (OUT / "invalid").mkdir(exist_ok=True)
    (OUT / "invalid" / "malformed_quotes.csv").write_text('Khasra No,Area\n"0012,1.5\n0013,2\n', encoding="utf-8")
    (OUT / "invalid" / "pdf_renamed_as.csv").write_bytes(b"%PDF-1.4\n%%EOF\n")
    (OUT / "invalid" / "header_only.csv").write_text("Khasra No,Area,District\n", encoding="utf-8")
    (OUT / "invalid" / "notes.txt").write_text("Unsupported extension example.\n", encoding="utf-8")


if __name__ == "__main__":
    bihar_csv()
    rajasthan_xlsx()
    pdfs()
    perf_csv()
    invalid_files()
    print("Synthetic samples written to", OUT)
