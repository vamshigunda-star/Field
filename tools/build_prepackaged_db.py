import os
import sqlite3
import csv
import uuid
import time
import re
import random

# Resolved from this file so the script works from any checkout, not just the original machine.
ASSETS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets")
DB_DIR = os.path.join(ASSETS_DIR, "database")
DB_PATH = os.path.join(DB_DIR, "alearning.db")

# Stamped into `catalog_metadata` so a fresh install can tell that the catalog shipped inside
# this database is already current and skip re-importing the CSVs at startup. Must match
# SeedDataManager.CATALOG_VERSION -- bump both together whenever the CSVs change, or the app
# will fall back to parsing the CSVs on every first launch (correct data, ~5.5s slower).
CATALOG_VERSION = "v31"

# Must match app/schemas/com.vamshi.field.data.AppDatabase/<ROOM_SCHEMA_VERSION>.json. Room
# refuses to open a prepackaged database whose identity hash disagrees with the compiled
# schema, so both values below have to be refreshed together whenever the schema changes.
ROOM_SCHEMA_VERSION = 17
ROOM_IDENTITY_HASH = "07d547486e7cae323e90278a671e827e"

os.makedirs(DB_DIR, exist_ok=True)
if os.path.exists(DB_PATH):
    os.remove(DB_PATH)

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()

def parse_double(val, default=None):
    if not val:
        return default
    val = val.strip()
    m = re.search(r"[-+]?\d*\.?\d+", val)
    if m:
        try:
            return float(m.group(0))
        except:
            return default
    return default

def parse_int(val, default=None):
    d = parse_double(val, default)
    return int(d) if d is not None else default

# 1. Create Room v14 schema
schema_statements = [
    "CREATE TABLE IF NOT EXISTS `individuals` (`id` TEXT NOT NULL, `firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `dateOfBirth` INTEGER NOT NULL, `sex` TEXT NOT NULL, `medicalAlert` TEXT, `isRestricted` INTEGER NOT NULL, `email` TEXT, `isActive` INTEGER NOT NULL, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_individuals_lastName_firstName` ON `individuals` (`lastName`, `firstName`)",
    "CREATE INDEX IF NOT EXISTS `index_individuals_isActive` ON `individuals` (`isActive`)",
    "CREATE TABLE IF NOT EXISTS `groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `location` TEXT, `cycle` TEXT, `category` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_groups_cycle_name` ON `groups` (`cycle`, `name`)",
    "CREATE TABLE IF NOT EXISTS `group_members` (`groupId` TEXT NOT NULL, `individualId` TEXT NOT NULL, `dateJoined` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `individualId`), FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`individualId`) REFERENCES `individuals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_group_members_individualId` ON `group_members` (`individualId`)",
    "CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)",
    "CREATE TABLE IF NOT EXISTS `test_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `sortOrder` INTEGER NOT NULL, `radarAxis` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `fitness_tests` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `isHigherBetter` INTEGER NOT NULL, `description` TEXT, `timingMode` TEXT NOT NULL, `inputParadigm` TEXT NOT NULL, `athletesPerHeat` INTEGER, `trialsPerAthlete` INTEGER NOT NULL, `validMin` REAL, `validMax` REAL, `interpretationStrategy` TEXT NOT NULL, `calculationConfig` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `youtubeId` TEXT, `source` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `test_categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    "CREATE INDEX IF NOT EXISTS `index_fitness_tests_categoryId` ON `fitness_tests` (`categoryId`)",
    "CREATE TABLE IF NOT EXISTS `norm_references` (`id` TEXT NOT NULL, `testId` TEXT NOT NULL, `variant` TEXT, `sex` TEXT NOT NULL, `ageMin` REAL NOT NULL, `ageMax` REAL NOT NULL, `minScore` REAL NOT NULL, `maxScore` REAL NOT NULL, `percentile` INTEGER NOT NULL, `classification` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`testId`) REFERENCES `fitness_tests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_norm_references_testId_variant_sex_ageMin` ON `norm_references` (`testId`, `variant`, `sex`, `ageMin`)",
    "CREATE TABLE IF NOT EXISTS `testing_events` (`id` TEXT NOT NULL, `groupId` TEXT, `name` TEXT NOT NULL, `date` INTEGER NOT NULL, `location` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_testing_events_groupId` ON `testing_events` (`groupId`)",
    "CREATE TABLE IF NOT EXISTS `test_results` (`id` TEXT NOT NULL, `eventId` TEXT NOT NULL, `individualId` TEXT NOT NULL, `testId` TEXT NOT NULL, `rawScore` REAL NOT NULL, `ageAtTime` REAL NOT NULL, `weightAtTime` REAL, `bodyWeightKg` REAL, `percentile` INTEGER, `classification` TEXT, `normVariantUsed` TEXT, `captureMethod` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`eventId`) REFERENCES `testing_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`individualId`) REFERENCES `individuals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`testId`) REFERENCES `fitness_tests`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    "CREATE INDEX IF NOT EXISTS `index_test_results_eventId` ON `test_results` (`eventId`)",
    "CREATE INDEX IF NOT EXISTS `index_test_results_individualId` ON `test_results` (`individualId`)",
    "CREATE INDEX IF NOT EXISTS `index_test_results_testId` ON `test_results` (`testId`)",
    "CREATE INDEX IF NOT EXISTS `index_test_results_individualId_testId_createdAt` ON `test_results` (`individualId`, `testId`, `createdAt`)",
    "CREATE INDEX IF NOT EXISTS `index_test_results_individualId_createdAt` ON `test_results` (`individualId`, `createdAt`)",
    "CREATE TABLE IF NOT EXISTS `event_test_cross_ref` (`eventId` TEXT NOT NULL, `testId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`eventId`, `testId`), FOREIGN KEY(`eventId`) REFERENCES `testing_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`testId`) REFERENCES `fitness_tests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_event_test_cross_ref_testId` ON `event_test_cross_ref` (`testId`)",
    "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `username` TEXT NOT NULL, `email` TEXT, `passwordHash` BLOB NOT NULL, `passwordSalt` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)",
    "CREATE TABLE IF NOT EXISTS `recommendation_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `icon` TEXT, `scope` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `recommendation_test_cross_ref` (`recommendationCategoryId` TEXT NOT NULL, `testId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `required` INTEGER NOT NULL, PRIMARY KEY(`recommendationCategoryId`, `testId`), FOREIGN KEY(`recommendationCategoryId`) REFERENCES `recommendation_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`testId`) REFERENCES `fitness_tests`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_recommendation_test_cross_ref_testId` ON `recommendation_test_cross_ref` (`testId`)",
    "CREATE TABLE IF NOT EXISTS `catalog_metadata` (`metaKey` TEXT NOT NULL, `metaValue` TEXT NOT NULL, PRIMARY KEY(`metaKey`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    f"INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '{ROOM_IDENTITY_HASH}')"
]

for stmt in schema_statements:
    cursor.execute(stmt)

now = int(time.time() * 1000)

# 2. Insert test_categories.csv
with open(os.path.join(ASSETS_DIR, "test_categories.csv"), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        cursor.execute(
            "INSERT INTO test_categories (id, name, description, sortOrder, radarAxis, createdAt, updatedAt, isDeleted, source) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'SEED')",
            (row["id"], row["name"], row.get("description") or None, parse_int(row.get("sortOrder"), 0), row.get("radarAxis") or None, now, now)
        )

# 3. Insert tests.csv
with open(os.path.join(ASSETS_DIR, "tests.csv"), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        cursor.execute(
            """INSERT INTO fitness_tests (
                id, categoryId, name, unit, isHigherBetter, description, timingMode, inputParadigm,
                athletesPerHeat, trialsPerAthlete, validMin, validMax, interpretationStrategy, calculationConfig,
                createdAt, updatedAt, isDeleted, youtubeId, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'SEED')""",
            (
                row["id"],
                row["categoryId"],
                row["name"],
                (row.get("unit") or "units").strip() or "units",
                1 if row.get("isHigherBetter", "").lower() == "true" else 0,
                row.get("description"),
                row.get("timingMode") or "MANUAL_ENTRY",
                row.get("inputParadigm") or "NUMERIC",
                parse_int(row.get("athletesPerHeat")),
                parse_int(row.get("trialsPerAthlete"), 1),
                parse_double(row.get("validMin")),
                parse_double(row.get("validMax")),
                row.get("interpretationStrategy") or "NORM_LOOKUP",
                row.get("calculationConfig"),
                now,
                now,
                row.get("youtube_id", "").strip() if len(row.get("youtube_id", "").strip()) == 11 else None
            )
        )

# 4. Insert norms (norms.csv)
norms_file = "norms.csv"
with open(os.path.join(ASSETS_DIR, norms_file), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)

    for row in reader:
        cursor.execute(
            """INSERT INTO norm_references (
                id, testId, variant, sex, ageMin, ageMax, minScore, maxScore, percentile, classification, createdAt, updatedAt, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SEED')""",
            (
                str(uuid.uuid4()),
                row["testId"],
                row.get("variant") or "Default",
                row.get("sex", "MALE").upper(),
                parse_double(row.get("ageMin"), 0.0),
                parse_double(row.get("ageMax"), 99.0),
                parse_double(row.get("minScore"), 0.0),
                parse_double(row.get("maxScore"), 999.0),
                parse_int(row.get("percentile"), 0),
                row.get("classification"),
                now,
                now
            )
        )

# 5. Insert recommendation_categories.csv
with open(os.path.join(ASSETS_DIR, "recommendation_categories.csv"), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        cursor.execute(
            "INSERT INTO recommendation_categories (id, name, description, icon, scope, sortOrder) VALUES (?, ?, ?, ?, ?, ?)",
            (row["id"], row["name"], row.get("description"), row.get("icon"), row.get("scope") or "POPULATION", parse_int(row.get("sortOrder"), 0))
        )

# 6. Insert recommendation_tests.csv
with open(os.path.join(ASSETS_DIR, "recommendation_tests.csv"), "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        cursor.execute(
            "INSERT INTO recommendation_test_cross_ref (recommendationCategoryId, testId, sortOrder, required) VALUES (?, ?, ?, ?)",
            (row["recommendationCategoryId"], row["testId"], parse_int(row.get("sortOrder"), 0), 1 if row.get("required", "").upper() == "TRUE" else 0)
        )

# 7. Insert Preloaded Groups, Athletes, Memberships, Events & Results
cursor.execute("INSERT INTO groups (id, name, location, cycle, category, createdAt, updatedAt, isDeleted) VALUES ('group_varsity_id', 'Varsity Football', 'Main Field', 'Fall 2026', 'TEAM', ?, ?, 0)", (now, now))
cursor.execute("INSERT INTO groups (id, name, location, cycle, category, createdAt, updatedAt, isDeleted) VALUES ('group_junior_id', 'Junior Basketball', 'Gymnasium', 'Winter 2026', 'TEAM', ?, ?, 0)", (now, now))

athletes = [
    ("athlete_alex", "Alex", "Mercer", 1201824000000, "MALE", None, 0),
    ("athlete_sarah", "Sarah", "Connor", 1244764800000, "FEMALE", "Asthma", 0),
    ("athlete_marcus", "Marcus", "Fenix", 1195516800000, "MALE", "Previous ACL Sprain", 0),
    ("athlete_lara", "Lara", "Croft", 1234569600000, "FEMALE", None, 0),
    ("athlete_john", "John", "Doe", 1229731200000, "MALE", None, 0)
]

for a in athletes:
    cursor.execute(
        "INSERT INTO individuals (id, firstName, lastName, dateOfBirth, sex, medicalAlert, isRestricted, email, isActive, notes, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1, NULL, ?, ?, 0)",
        (a[0], a[1], a[2], a[3], a[4], a[5], a[6], now, now)
    )

memberships = [
    ("group_varsity_id", "athlete_alex"),
    ("group_varsity_id", "athlete_sarah"),
    ("group_varsity_id", "athlete_marcus"),
    ("group_varsity_id", "athlete_lara"),
    ("group_junior_id", "athlete_lara"),
    ("group_junior_id", "athlete_john")
]
for m in memberships:
    cursor.execute("INSERT INTO group_members (groupId, individualId, dateJoined) VALUES (?, ?, ?)", (m[0], m[1], now))

test_ids = ["test_push_up", "test_5_10_5_shuttle_run", "test_shuttle_run", "test_1rm_squat", "test_t"]
base_scores = {
    "athlete_alex": {"test_push_up": 40.0, "test_5_10_5_shuttle_run": 5.5, "test_shuttle_run": 30.0, "test_1rm_squat": 100.0, "test_t": 11.0},
    "athlete_sarah": {"test_push_up": 25.0, "test_5_10_5_shuttle_run": 6.0, "test_shuttle_run": 25.0, "test_1rm_squat": 60.0, "test_t": 12.0},
    "athlete_marcus": {"test_push_up": 15.0, "test_5_10_5_shuttle_run": 6.5, "test_shuttle_run": 15.0, "test_1rm_squat": 120.0, "test_t": 13.0},
    "athlete_lara": {"test_push_up": 35.0, "test_5_10_5_shuttle_run": 5.8, "test_shuttle_run": 32.0, "test_1rm_squat": 80.0, "test_t": 10.5}
}
improvement_factor = {
    "test_push_up": 2.5,
    "test_5_10_5_shuttle_run": -0.08,
    "test_shuttle_run": 2.0,
    "test_1rm_squat": 4.0,
    "test_t": -0.2
}

one_year_ms = 31536000000
ms_per_event = one_year_ms // 10

random.seed(42)

for i in range(10):
    event_id = f"event_benchmark_{i}"
    event_date = now - one_year_ms + (i * ms_per_event)
    cursor.execute(
        "INSERT INTO testing_events (id, groupId, name, date, location, notes, createdAt) VALUES (?, 'group_varsity_id', ?, ?, 'Main Field', NULL, ?)",
        (event_id, f"Benchmark Testing Round {i + 1}", event_date, event_date)
    )
    for sort_idx, t_id in enumerate(test_ids):
        cursor.execute(
            "INSERT INTO event_test_cross_ref (eventId, testId, sortOrder) VALUES (?, ?, ?)",
            (event_id, t_id, sort_idx)
        )
    for athlete_id, scores in base_scores.items():
        for t_id in test_ids:
            base_score = scores[t_id]
            factor = improvement_factor[t_id]
            noise = (random.random() - 0.5) * abs(factor)
            final_score = round(base_score + (factor * i) + noise, 2)
            pct_base = 30 + (i * 5)
            pct = min(99, max(1, pct_base + random.randint(0, 10)))
            cls = "SUPERIOR" if pct >= 80 else ("HEALTHY" if pct >= 40 else "NEEDS_IMPROVEMENT")
            cursor.execute(
                """INSERT INTO test_results (
                    id, eventId, individualId, testId, rawScore, ageAtTime, weightAtTime, bodyWeightKg,
                    percentile, classification, normVariantUsed, captureMethod, createdAt
                ) VALUES (?, ?, ?, ?, ?, 18.0, NULL, NULL, ?, ?, NULL, 'MANUAL_ENTRY', ?)""",
                (str(uuid.uuid4()), event_id, athlete_id, t_id, final_score, pct, cls, event_date)
            )

cursor.execute(
    "INSERT OR REPLACE INTO catalog_metadata (metaKey, metaValue) VALUES (?, ?)",
    ("catalog_version", CATALOG_VERSION),
)

conn.commit()
cursor.execute(f"PRAGMA user_version = {ROOM_SCHEMA_VERSION}")
conn.commit()
cursor.execute("VACUUM")
conn.close()
print("Successfully generated pre-packaged database:", DB_PATH)
