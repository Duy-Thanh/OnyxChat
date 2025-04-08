import os
import sys
from collections import defaultdict
import math

# Language configuration
language_config = {
    'c':      {'single': ['//'],    'multi': [('/*', '*/')]},
    'cpp':    {'single': ['//'],    'multi': [('/*', '*/')]},
    'h':      {'single': ['//'],    'multi': [('/*', '*/')]},
    'java':   {'single': ['//'],    'multi': [('/*', '*/')]},
    'js':     {'single': ['//'],    'multi': [('/*', '*/')]},
    'py':     {'single': ['#'],     'multi': [('"""', '"""'), ("'''", "'''")]},
    'html':   {'single': [],        'multi': [('<!--', '-->')]},
    'php':    {'single': ['//', '#'],'multi': [('/*', '*/')]},
    'swift':  {'single': ['//'],    'multi': [('/*', '*/')]},
    'asm':    {'single': [';'],     'multi': []},
    's':      {'single': [';'],     'multi': []},
    # Extend as needed
}

def get_extension_language(filename):
    ext = filename.rsplit('.', 1)[-1].lower()
    return ext if ext in language_config else None

def count_sloc_in_file(filepath, ext):
    config = language_config.get(ext)
    if not config:
        return 0

    sloc = 0
    in_multiline_comment = False
    with open(filepath, 'r', errors='ignore') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue

            if in_multiline_comment:
                for _, end in config['multi']:
                    if end in line:
                        line = line.split(end, 1)[1]
                        in_multiline_comment = False
                        break
                else:
                    continue
                if not line.strip():
                    continue

            for token in config['single']:
                if token in line:
                    line = line.split(token, 1)[0]

            for start, end in config['multi']:
                if start in line:
                    before, after = line.split(start, 1)
                    if end in after:
                        after = after.split(end, 1)[1]
                        line = before + after
                    else:
                        line = before
                        in_multiline_comment = True

            if line.strip():
                sloc += 1
    return sloc

def scan_directory(base_path):
    dir_language_counts = defaultdict(lambda: defaultdict(int))
    language_totals = defaultdict(int)

    for root, _, files in os.walk(base_path):
        rel_dir = os.path.relpath(root, base_path)
        if rel_dir == ".":
            rel_dir = "top_dir"

        for file in files:
            if '.' not in file:
                continue
            ext = get_extension_language(file)
            if not ext:
                continue

            full_path = os.path.join(root, file)
            count = count_sloc_in_file(full_path, ext)
            if count > 0:
                dir_language_counts[rel_dir][ext] += count
                language_totals[ext] += count
            elif rel_dir not in dir_language_counts:
                dir_language_counts[rel_dir] = {}

    return dir_language_counts, language_totals

def cocomo_model(total_sloc):
    ksloc = total_sloc / 1000.0
    person_months = 2.4 * (ksloc ** 1.05)
    schedule_months = 2.5 * (person_months ** 0.38)
    cost = person_months * 56286 * 2.4
    return {
        'person_months': person_months,
        'schedule_months': schedule_months,
        'developers': person_months / schedule_months if schedule_months > 0 else 0,
        'cost': cost,
    }

def format_output(dir_lang_counts, lang_totals):
    print("Computing results.\n")
    print("SLOC\tDirectory\tSLOC-by-Language (Sorted)")

    total_sloc = 0
    for directory in sorted(dir_lang_counts):
        langs = dir_lang_counts[directory]
        sloc_dir = sum(langs.values())
        total_sloc += sloc_dir
        if langs:
            sorted_langs = sorted(langs.items(), key=lambda x: -x[1])
            lang_str = ",".join(f"{k}={v}" for k, v in sorted_langs)
        else:
            lang_str = "(none)"
        print(f"{sloc_dir:<8}{directory:<16}{lang_str}")

    print("\n\nTotals grouped by language (dominant language first):")
    total_sloc = sum(lang_totals.values())
    sorted_langs = sorted(lang_totals.items(), key=lambda x: -x[1])
    for lang, count in sorted_langs:
        percent = (count / total_sloc * 100) if total_sloc else 0
        print(f"{lang:<13}{count:>7} ({percent:.2f}%)")

    print("\n")
    print(f"Total Physical Source Lines of Code (SLOC)                = {total_sloc:,}")
    metrics = cocomo_model(total_sloc)
    print(f"Development Effort Estimate, Person-Years (Person-Months) = {metrics['person_months']/12:.2f} ({metrics['person_months']:.2f})")
    print(" (Basic COCOMO model, Person-Months = 2.4 * (KSLOC**1.05))")
    print(f"Schedule Estimate, Years (Months)                         = {metrics['schedule_months']/12:.2f} ({metrics['schedule_months']:.2f})")
    print(" (Basic COCOMO model, Months = 2.5 * (person-months**0.38))")
    print(f"Estimated Average Number of Developers (Effort/Schedule)  = {metrics['developers']:.2f}")
    print(f"Total Estimated Cost to Develop                           = $ {int(metrics['cost']):,}")
    print(" (average salary = $56,286/year, overhead = 2.40).")
    print("SLOCCount, Copyright (C) 2001-2004 David A. Wheeler")
    print("SLOCCount is Open Source Software/Free Software, licensed under the GNU GPL.")
    print("SLOCCount comes with ABSOLUTELY NO WARRANTY, and you are welcome to")
    print("redistribute it under certain conditions as specified by the GNU GPL license;")
    print("see the documentation for details.")
    print("Please credit this data as \"generated using David A. Wheeler's 'SLOCCount'.\"")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python sloc_counter.py <source_directory>")
        sys.exit(1)

    target_dir = sys.argv[1]
    if not os.path.exists(target_dir):
        print("Directory not found:", target_dir)
        sys.exit(1)

    dir_counts, lang_totals = scan_directory(target_dir)
    format_output(dir_counts, lang_totals)
