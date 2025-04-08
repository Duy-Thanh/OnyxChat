import os
import sys

# Define language configurations
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
    # add more as needed...
}

def get_language_by_extension(filename):
    ext = filename.rsplit('.', 1)[-1].lower()
    return language_config.get(ext)

def count_sloc_in_file(filepath):
    ext = filepath.rsplit('.', 1)[-1].lower()
    lang = language_config.get(ext)
    if not lang:
        return 0  # unsupported file type

    sloc = 0
    in_multiline_comment = False
    multi_comment_starts = [start for start, _ in lang['multi']]
    multi_comment_ends   = [end for _, end in lang['multi']]

    with open(filepath, 'r', errors='ignore') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue

            if in_multiline_comment:
                for end in multi_comment_ends:
                    if end in line:
                        line = line.split(end, 1)[1]
                        in_multiline_comment = False
                        break
                else:
                    continue  # still inside comment
                if not line.strip():
                    continue  # line ends with multiline comment

            # Remove single-line comments
            for token in lang['single']:
                if token in line:
                    line = line.split(token, 1)[0]

            # Handle start of multiline comment
            for start, end in lang['multi']:
                if start in line:
                    before, after = line.split(start, 1)
                    if end in after:
                        # comment starts and ends on same line
                        after = after.split(end, 1)[1]
                        line = before + after
                    else:
                        line = before
                        in_multiline_comment = True

            if line.strip():
                sloc += 1

    return sloc

def scan_directory(path):
    total_sloc = 0
    per_file = {}
    for root, _, files in os.walk(path):
        for file in files:
            full_path = os.path.join(root, file)
            if '.' not in file:
                continue
            lang = get_language_by_extension(file)
            if not lang:
                continue
            count = count_sloc_in_file(full_path)
            total_sloc += count
            per_file[full_path] = count
    return total_sloc, per_file

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python sloc_counter.py <source_directory>")
        sys.exit(1)

    target_dir = sys.argv[1]
    if not os.path.exists(target_dir):
        print("Directory not found:", target_dir)
        sys.exit(1)

    total, file_counts = scan_directory(target_dir)
    for path, count in file_counts.items():
        print(f"{path}: {count} LOC")
    print("\nTotal SLOC:", total)
