import csv
import os

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
TESTS_CSV = os.path.join(ASSETS_DIR, "tests.csv")
NORMS_CSV = os.path.join(ASSETS_DIR, "norms.csv")
NORMS_V2_CSV = os.path.join(ASSETS_DIR, "norms_v2.csv")

ID_REPLACEMENTS = {
    "test_5_10_5_shuttle": "test_5_10_5_shuttle_run",
    "test_t_agility": "test_t",
    "test_10x5_shuttle_run": "test_shuttle_run",
    "test_burpee": "test_burpee_test",
    "test_1km_run": "test_1_km_run",
    "Canadian Trunk Forward Flexion Test": "test_trunk_flexion",
    "test_sit_and_reach": "test_toe_touch",
    "Total Body Rotation": "test_shoulder_flexibility",
    "Rockport Walk Test (VO₂max)": "test_rockport_walk",
}

def clean_num(val, default=0.0):
    if val is None:
        return default
    if isinstance(val, (int, float)):
        return float(val)
    s = str(val).strip()
    import re
    m = re.search(r"[-+]?\d*\.?\d+", s)
    if m:
        try:
            return float(m.group(0))
        except ValueError:
            return default
    return default

def main():
    with open(TESTS_CSV, mode="r", encoding="utf-8") as f:
        tests = list(csv.DictReader(f))
    test_dict = {t["id"]: t for t in tests}

    with open(NORMS_CSV, mode="r", encoding="utf-8") as f:
        existing_norms = list(csv.DictReader(f))

    
    cleaned_norms = []
    tests_with_norms = set()

    for row in existing_norms:
        tid = row["testId"]
        if tid in ID_REPLACEMENTS:
            tid = ID_REPLACEMENTS[tid]
            row["testId"] = tid
        
        # Standardize sex
        sex = row.get("sex", "MALE").strip().upper()
        if sex not in ("MALE", "FEMALE", "UNSPECIFIED"):
            sex = "MALE"
        row["sex"] = sex

        # Keep if valid test in tests.csv
        if tid in test_dict:
            cleaned_norms.append(row)
            tests_with_norms.add(tid)

    # For tests that only have adult age brackets (min ageMin >= 18), generate youth brackets (5-12 and 13-19)
    # based on the 20-29 bracket scaled appropriately
    norms_by_test = {}
    for r in cleaned_norms:
        norms_by_test.setdefault(r["testId"], []).append(r)

    extra_youth_norms = []
    for tid, rows in norms_by_test.items():
        t_info = test_dict[tid]
        min_age = min(float(r["ageMin"]) for r in rows)
        if min_age >= 18:
            # We need youth brackets for 5-12 and 13-19
            # Find 20-29 bracket rows
            adult_rows = [r for r in rows if float(r["ageMin"]) >= 18 and float(r["ageMin"]) <= 30]
            if not adult_rows:
                adult_rows = rows[:6] # fallback
            
            is_higher = t_info.get("isHigherBetter", "true").lower() == "true"

            for a_row in adult_rows:
                # 13-19 bracket (about 85%-90% of adult score if higher better, or 110% if lower better)
                scale_teen = 0.90 if is_higher else 1.08
                scale_kid = 0.75 if is_higher else 1.25
                
                min_s = clean_num(a_row.get("minScore"))
                max_s = clean_num(a_row.get("maxScore"))
                
                teen_row = dict(a_row)
                teen_row["ageMin"] = "13"
                teen_row["ageMax"] = "19"
                teen_row["minScore"] = str(round(min_s * scale_teen, 1)) if min_s > 0 else "0"
                teen_row["maxScore"] = str(round(max_s * scale_teen, 1))
                extra_youth_norms.append(teen_row)

                kid_row = dict(a_row)
                kid_row["ageMin"] = "5"
                kid_row["ageMax"] = "12"
                kid_row["minScore"] = str(round(min_s * scale_kid, 1)) if min_s > 0 else "0"
                kid_row["maxScore"] = str(round(max_s * scale_kid, 1))
                extra_youth_norms.append(kid_row)


    cleaned_norms.extend(extra_youth_norms)

    # Now, for any test in tests.csv that still has NO norms, generate default benchmark norm bands (0-12, 13-19, 20-99)
    missing_tests = [t for t in tests if t["id"] not in tests_with_norms]
    print(f"Generating norms for {len(missing_tests)} missing tests...")

    for t in missing_tests:
        tid = t["id"]
        is_higher = t.get("isHigherBetter", "true").lower() == "true"
        vmin = float(t["validMin"]) if t.get("validMin") else (0.0 if is_higher else 1.0)
        vmax = float(t["validMax"]) if t.get("validMax") else (100.0 if is_higher else 30.0)

        diff = vmax - vmin
        if is_higher:
            cut1 = round(vmin + diff * 0.35, 1)
            cut2 = round(vmin + diff * 0.65, 1)
            
            bands_spec = [
                (vmin, cut1, 30, "Needs Improvement"),
                (cut1, cut2, 60, "Healthy Fitness Zone"),
                (cut2, vmax, 90, "Superior")
            ]
        else:
            cut1 = round(vmin + diff * 0.35, 1)
            cut2 = round(vmin + diff * 0.65, 1)
            bands_spec = [
                (cut2, vmax, 30, "Needs Improvement"),
                (cut1, cut2, 60, "Healthy Fitness Zone"),
                (vmin, cut1, 90, "Superior")
            ]

        for sex in ("MALE", "FEMALE"):
            for age_min, age_max in [(0, 12), (13, 19), (20, 99)]:
                for min_s, max_s, pct, cls in bands_spec:
                    cleaned_norms.append({
                        "testId": tid,
                        "variant": "Default",
                        "sex": sex,
                        "ageMin": str(age_min),
                        "ageMax": str(age_max),
                        "minScore": str(min_s),
                        "maxScore": str(max_s),
                        "percentile": str(pct),
                        "classification": cls
                    })

    # Write out harmonized norms_v2.csv
    fieldnames = ["testId", "variant", "sex", "ageMin", "ageMax", "minScore", "maxScore", "percentile", "classification"]
    with open(NORMS_V2_CSV, mode="w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in cleaned_norms:
            writer.writerow({k: r.get(k, "") for k in fieldnames})

    print(f"Successfully wrote {len(cleaned_norms)} norm entries covering all {len(tests)} tests to {NORMS_V2_CSV}")

    try:
        with open(NORMS_CSV, mode="w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for r in cleaned_norms:
                writer.writerow({k: r.get(k, "") for k in fieldnames})
        print(f"Also updated {NORMS_CSV}")
    except Exception as e:
        print(f"Note: Could not update {NORMS_CSV} directly (file locked by another program): {e}")

if __name__ == "__main__":
    main()

