# SmartLibrary – Intelligent Digital Library Platform (Console Version)

Java console application that runs entirely in the **Eclipse Console** – no windows, no Swing.

**Implemented:** secure first-run Admin setup, login, role-based access control, CSV user storage,
four role menus (Admin, Librarian, Student, Teacher), Admin user management, password change,
**book import from .txt files**, **View All Books**, and the **two-step book search using the DSA
Module 1 string-matching algorithms** (KMP, Rabin–Karp, Z Algorithm, Aho–Corasick) with time
complexity, measured time and comparison counts, and **Compare Documents** using the DSA Module 2
suffix structures (Suffix Array with SA-IS, LCP with Kasai, Suffix Tree with Ukkonen's algorithm),
plus DSA Module 3 dynamic programming: **"Did you mean?"** spelling correction (Levenshtein /
Damerau-Levenshtein), **word alignment** of two books (Needleman-Wunsch / Smith-Waterman) and
**Search Statistics** with an **Optimal Binary Search Tree**; DSA Module 4 network flow
(**Reviewer Assignment** with Edmonds-Karp, bipartite matching and König's theorem); DSA Module 5
NP-completeness (**Document Conflict Analysis**: vertex cover exact vs 2-approximation, and the
CLIQUE → INDEPENDENT-SET → VERTEX-COVER reductions); and DSA Module 6 randomized and parallel
algorithms (**Library Analytics**: Randomized QuickSort, Reservoir Sampling, Parallel Reduce,
Blelloch Scan and Brent's theorem).
Borrowing, research papers and recommendations are still **planned** and are clearly labelled
`PLANNED - NOT YET IMPLEMENTED` (no fake data is shown).

- Language: Java 17+ (standard library only – no external JARs)
- Interface: text menus in the console
- Storage: `data/users.csv`, `data/books.csv`, book texts in `data/books/*.txt`
- IDE: Eclipse

---

## 1. Import into Eclipse

The zip already contains Eclipse project files (`.project`, `.classpath`).

1. Unzip `SmartLibrary.zip`, e.g. to `D:\Projects\SmartLibrary`.
2. Eclipse → **File → Import… → General → Existing Projects into Workspace → Next**.
3. **Select root directory** → browse to the `SmartLibrary` folder → tick **SmartLibrary** → **Finish**.
4. If Eclipse shows a JRE error: right-click project → **Properties → Java Build Path → Libraries** →
   remove the JRE entry → **Add Library… → JRE System Library** → choose a JDK **17 or newer**.

If you imported an older version of this project, delete it from Eclipse first
(right-click → Delete, tick *Delete project contents on disk* only if you don't need its data).

## 2. Run

Right-click `src/com.smartlibrary/Main.java` → **Run As → Java Application**.

Everything happens in the **Console** view at the bottom of Eclipse
(if it isn't visible: **Window → Show View → Console**). Click inside the Console and type your
answers, pressing **Enter** after each one. Menus are numbered – type the number and press Enter.

> The Eclipse Console cannot hide typed characters, so **passwords are visible while you type them**.
> They are still stored only as secure hashes.

## 3. First run – creating the Admin (one time only)

There is **no default Admin password** anywhere in the code.

On first run no Admin exists, so the console shows a box like this:

```
==================================================
 SmartLibrary - FIRST RUN SETUP
 Setup code: K7QM-XR4P-9TWD
 (also saved in D:\Projects\SmartLibrary\data\setup-code.txt)
 Type it below when asked for the 'Setup code'.
==================================================
```

1. Type that code at the `Setup code :` prompt (your code is random and different every first run).
2. Enter the Admin's full name, username, email, password and confirmation.
3. The code file is deleted and setup is permanently disabled while an Admin exists.
4. Choose **1. Login** and log in as the Admin.
5. **Manage Users → Create user** to add Students, Teachers and Librarians with temporary passwords.
6. Each new user logs in with the temporary password and is **forced to set a new password** first.

To choose your own setup code instead: **Run → Run Configurations → Main → Environment** tab →
add `SMARTLIBRARY_SETUP_SECRET` with any value, and type that value as the setup code.

To start over: reset `data/users.csv` to only its first (header) line and run again.

## 4. Menus

```
Main Menu:  1. Login   0. Exit

Admin:      1. Manage Users  2. Manage Library Resources  3. System Statistics
            4. Search Statistics (Optimal BST)  5. Reviewer Assignment (Network Flow)
            6. Document Conflict Analysis (NP-Completeness)  7. Library Analytics (Randomized + Parallel)
            8. Account Settings  0. Logout
              Manage Users: 1. List all users  2. Search users  3. Create user
                            4. Update user  5. Activate / Deactivate user  0. Back
              Manage Library Resources: 1. List all books  2. Import a book (.txt file)  0. Back
Librarian:  1. View All Books  2. Manage Books (list / import .txt)  3. Search Documents
            4. Compare Documents  5. Search Statistics (Optimal BST)  6. Reviewer Assignment (Network Flow)
            7. Document Conflict Analysis (NP-Completeness)  8. Library Analytics (Randomized + Parallel)
            9. Issue and Return Books  10. Borrowing Records  11. Account Settings  0. Logout
Student:    1. View All Books  2. Search Books  3. Compare Documents
            4. Document Conflict Analysis (NP-Completeness)  5. Search Research Papers
            6. My Borrowing History  7. Due Dates  8. Account Settings  0. Logout
Teacher:    1. View All Books  2. Search Academic Resources  3. My Review Assignments
            4. Compare Documents  5. Document Conflict Analysis (NP-Completeness)
            6. Recommend Resources  7. Account Settings  0. Logout
```

| Role | Implemented now |
|---|---|
| Admin | User management, live statistics, list and import books, Search Statistics, Reviewer Assignment, Conflict Analysis, Library Analytics, change password |
| Librarian | View All Books, import .txt books, two-step search, Compare Documents, Search Statistics, Reviewer Assignment, Conflict Analysis, Library Analytics, change password |
| Teacher | View All Books, two-step search, My Review Assignments, Compare Documents, Conflict Analysis, change password |
| Student | View All Books, two-step search, Compare Documents, Conflict Analysis, change password |

Issue/Return, Borrowing Records, Search Research Papers, Recommend Resources and Due Dates show
what is planned.

When updating a user, press **Enter** to keep any value shown in `[brackets]`.

### Sample accounts (in `data/users.csv`)

| Role | Usernames | Temporary password |
|---|---|---|
| Librarian | `lib.ramesh`, `lib.lakshmi`, `lib.suresh` | `Ramesh@101`, `Lakshmi@102`, `Suresh@103` |
| Teacher | `tch.sita`, `tch.venkat`, `tch.anitha` | `Sita@104`, `Venkat@105`, `Anitha@106` |
| Student | `stu.ravi`, `stu.priya`, `stu.kiran`, `stu.divya` | `Ravi@107`, `Priya@108`, `Kiran@109`, `Divya@110` |
| Student (inactive) | `stu.mahesh` | `Mahesh@111` |

Each sample user must set a new password on first login. There is no sample Admin – create it
through first-run setup (section 3).

## 5. Books and string matching (DSA Module 1)

### Books
- Each book is a `.txt` file in `data/books/`, listed in `data/books.csv`
  (`bookId,title,author,category,fileName`). Five sample books are included.
- **Import** (Librarian or Admin): Manage Books → Import a book → paste the full path of a `.txt`
  file (Windows: Shift + right-click the file → *Copy as path*), then title, author and category.
  The file is copied into `data/books/`. Only `.txt` files up to 10 MB; title + author must be unique.
- **View All Books**: every book grouped by category; pick a number to search inside that book.

### Two-step search
1. **Find a book** – type part of a title, author or category (`data struct`) and choose an
   algorithm. A linear search runs that algorithm on every book's "title | author | category".
   Books are ranked by matches, and **similar books** (same category) are listed too.
   Press Enter on an empty search to list all books.
2. **Search inside the book** – type a pattern (`binary tree`) or several separated by commas
   (`tree, graph, heap`) and choose an algorithm. Each match is shown with its line and column.

Both steps use the same algorithm menu (the `switch` is in `Algorithm.fromMenuChoice`):

| # | Algorithm | Time complexity |
|---|---|---|
| 1 | KMP | O(n + m) |
| 2 | Rabin–Karp | O(n + m) average, O(n·m) worst |
| 3 | Z Algorithm | O(n + m) |
| 4 | Aho–Corasick | O(n + M + z) – many patterns in one pass |
| 5 | Run ALL and compare | table of matches, time (ms), comparisons, complexity |

n = text length, m = pattern length, M = total pattern length, z = number of matches.
With k patterns, KMP / Rabin–Karp / Z scan the text k times: O(k·(n + m)).
Search is case-insensitive. Details: `src/com/smartlibrary/dsa/README.md`.

## 6. Compare Documents (DSA Module 2 – document similarity)

Compares **two books** and shows the **text they have in common**, the **longest common phrase**
and a **similarity %**, then lets you look up any phrase in both books.

**What you are asked:**
1. Pick the first and second book from the list (they must be different).
2. Method: `1` Suffix Array (SA-IS) + LCP (Kasai), `2` Suffix Tree (Ukkonen), `3` Run BOTH and compare,
   `4` Needleman-Wunsch, `5` Smith-Waterman (Module 3 – see section 7).
3. For methods 1–3: minimum phrase length in characters (press Enter for 10) – shorter matches are ignored.
4. After the results of 1–3: *Find a phrase in both books* (press Enter on an empty line to skip) –
   shows how many times and on which line it appears in each book.

| Slide point | Where it is used | Time |
|---|---|---|
| **Suffix Array** – document indexing | Index of both books; *Find a phrase* uses binary search on it | O(m log n) per lookup |
| **SA-IS** – large-scale construction | Builds the suffix array (the naive suffix sort time is shown next to it) | O(n) |
| **LCP / Kasai** – common phrases | Neighbouring suffixes from different books → common text | O(n) |
| **Suffix Tree** (Ukkonen) – substring retrieval | *Find a phrase* walks the tree; also finds the common text a second way | O(n) build, O(m) lookup |

**How the common text is found:** for every position of the shorter book, the method finds the
longest text starting there that also occurs in the other book (matching statistics). Runs of at
least the minimum length are trimmed to whole words and do not cross sentence ends.
Chapter headings (`Chapter 3: ...`) are ignored. Both methods always give the same result –
option 3 checks this.

**Similarity %** = letters/digits of the shorter book inside common phrases ÷ letters/digits of
the shorter book × 100. Identical books give 100%; the sample books share only a few phrases
(e.g. Data Structures vs Algorithms Explained ≈ 3%, sharing "breadth first search"). To demo
copy detection, import a `.txt` that copies part of a sample book and compare the two.

Books can be compared when both together are at most 1,000,000 characters.

## 7. Advanced Dynamic Programming (DSA Module 3)

| Slide point | Feature | Where | Time |
|---|---|---|---|
| **Levenshtein / Damerau-Levenshtein** – search-query correction | **"Did you mean?"** | Search Books, both steps, when a word is not found | O(n × m) per word pair |
| **Needleman-Wunsch / Smith-Waterman** – sequence-based document comparison | Word-by-word alignment of two books | Compare Documents → options 4 and 5 | O(n × m), n, m = words |
| **Optimal Binary Search Tree** – frequently searched keys | **Search Statistics** | Librarian and Admin menus | O(n³) DP |

### "Did you mean?" (Levenshtein / Damerau-Levenshtein)
If a search finds nothing (Step 1) or a pattern is not in the book (Step 2), each unknown word is
compared with the known words – titles/authors/categories in Step 1, the book's own words in Step 2 –
and the closest ones are offered:
```
  Did you mean?   (edit distance by dynamic programming, O(n x m) per word pair)
| # | Suggestion        | Damerau-Levenshtein | Levenshtein |
| 1 | deadlock, process | 3                   | 3           |
Use suggestion number (0 = no): 1
```
- **Levenshtein**: insert, delete or replace one letter = 1 edit (`garph` → `graph` = 2).
- **Damerau-Levenshtein**: also swapping two neighbouring letters = 1 edit (`garph` → `graph` = 1).
- Words up to 4 letters may differ by 1 edit, up to 8 letters by 2, longer by 3.

### Word alignment (Needleman-Wunsch / Smith-Waterman)
The two books are compared **word by word**, like DNA sequences. Scoring: same word +2, different
word −1, gap (word added or removed) −1.
- **Needleman-Wunsch (global)**: aligns both books from start to end – alignment score, matching /
  different / gap counts and identity %.
- **Smith-Waterman (local)**: finds the **best-matching passage** even if some words were changed,
  added or removed – shown with line numbers and an aligned view (`|` same word, `x` different word,
  `-` gap). This catches **reworded copying** that exact phrase matching (Module 2) misses.
- The first 3,000 words of each book are used.

### Search Statistics (Optimal BST)
Every successful search keyword is counted in `data/search_log.csv`. Search Statistics builds an
**Optimal Binary Search Tree** from those counts so frequently searched keywords sit near the root:
```
  Avg comparisons, Optimal BST: 2.00
  Avg comparisons, balanced   : 2.45  (same keys, ignoring frequency)
Look up a keyword in the tree (Enter = back): process
  Found "process": Optimal BST 1 comparison(s), balanced BST 3.
```
If nothing has been searched yet you can view clearly labelled **DEMO** frequencies. The 50 most
searched keywords are used.

When updating a user, press **Enter** to keep any value shown in `[brackets]`.

## 8. Network Flow (DSA Module 4) – Reviewer Assignment

**Who:** Librarian and Admin plan it; Teachers see their papers in **My Review Assignments**.

Reviewers are active **Teachers** with a profile in `data/reviewers.csv`
(`username,expertise,conflictBookIds`; lists separated by `;`). A reviewer is **eligible** for a book
when the book's category is one of their expertise areas and the book is not in their conflict list.
Sample profiles: `tch.sita` (Computer Science, conflict with book 1), `tch.venkat` (Computer Science,
Mathematics), `tch.anitha` (Biology, Mathematics, conflict with book 5). Option 4 edits profiles.

| Slide point | Where | Time |
|---|---|---|
| **Ford-Fulkerson / Edmonds-Karp** – resource distribution | Option 2: network `source → reviewers (capacity c) → papers (1) → sink`; max flow = most papers assigned | O(V·E²) |
| **Bipartite Matching** – papers to reviewers | Option 3: the same network with capacity 1 = maximum matching | O(V·E²) |
| **König's theorem** – reviewer-paper conflict analysis | Option 3: minimum vertex cover from the residual graph; its size = matching size, and it shows the bottleneck | O(V + E) after the flow |

With the samples and 2 papers per reviewer all 5 books are assigned; with 1 paper each, König shows
at most 3 can be matched. Saving writes `data/review_assignments.csv`.

## 9. NP-Completeness and Approximation (DSA Module 5) – Document Conflict Analysis

**Who:** everyone. Each book is a vertex; two books **conflict** (edge) when their text overlaps –
Compare Documents similarity (Module 2, suffix tree) at least the threshold (press Enter for 1 %).

| Slide point | Shown as |
|---|---|
| **VERTEX-COVER** (NP-complete) | Exact minimum cover by checking all 2^n subsets – fewest books to check so every overlap is checked |
| **Vertex-Cover 2-approximation** | Take an unchecked conflict, add both books, repeat – O(V + E), never more than 2× the optimum |
| **CLIQUE → INDEPENDENT-SET → VERTEX-COVER** | Max independent set = all books − min cover (books with no overlap); the same set is a clique in the complement graph; the largest clique = books that all overlap |

All three sets are verified in the output. With the samples (threshold 1 %): conflicts B1–B2, B1–B3,
B2–B3; minimum cover {B1, B2}; independent set {B3, B4, B5}; largest clique {B1, B2, B3}.
Up to 20 books are analysed.

## 10. Randomized and Parallel Algorithms (DSA Module 6) – Library Analytics

**Who:** Librarian and Admin. Every search, opened book and comparison is appended to
`data/usage_log.csv` (the usage stream).

| Slide point | Option | Shown as |
|---|---|---|
| **Randomized QuickSort** – ranking documents | 1 | Books ranked by word count; comparisons vs 2n ln n; sorted-input demo: random pivot ≈ 26,000 comparisons vs first-element pivot 1,999,000 (n²/2). Search Books also ranks results with it. |
| **Reservoir Sampling** – library-usage stream | 2 | k random events from the log in one pass with O(k) memory; action counts from the same pass. Empty log → clearly labelled DEMO stream of 100,000 events. |
| **Parallel Reduce** – document statistics | 3 | Word count of every book with fork/join (split in halves, add partial counts), checked against a sequential count |
| **Blelloch Scan** – cumulative statistics | 3 | Exclusive / inclusive prefix sums of the word counts (up-sweep + down-sweep) |
| **Brent's theorem** – parallel processing | 3 | T_p ≤ W/p + S for p = 1, 2, 4, 8 and this computer's processors |

## 11. Data folder

By default the app uses `data/` **relative to the working directory**. In Eclipse the working
directory is the project folder, so `SmartLibrary/data/users.csv` is used. The first console line
shows the exact path: `Data folder: D:\Projects\SmartLibrary\data`.

To check it: **Run → Run Configurations… → Main → Arguments tab → Working directory**
(keep *Default: ${workspace_loc:SmartLibrary}*).

To use another folder (first one wins):

| Option | Where to set it in Eclipse |
|---|---|
| VM argument `-Dsmartlibrary.dataDir=D:\SmartLibraryData` | Run Configurations → Arguments → **VM arguments** |
| Environment variable `SMARTLIBRARY_DATA_DIR` | Run Configurations → **Environment** tab |

If the folder or `users.csv` is missing, it is created automatically with the header.

## 12. Security design

- **One login prompt, no role choice.** The menu shown depends only on the role stored in `users.csv`.
- **Permission checks in the service layer.** Every admin operation in `UserService` re-reads the
  logged-in user from the CSV and checks `role == ADMIN`; menus are not the only protection.
  The session is also re-checked before every menu action, so a deactivated user is logged out.
- **Passwords:** PBKDF2-HMAC-SHA256, 100,000 iterations, 16-byte random salt, constant-time comparison.
  Stored as `pbkdf2$iterations$salt$hash`. Anything else (e.g. `HASH_PLACEHOLDER`) can never log in.
- Password rules: 8–128 characters, at least one letter and one digit, no spaces.
- Hashes are never printed: `UserService` returns users without them and `User.toString()` omits them.
- Wrong password and unknown username give the **same** message and timing; inactive status is only
  revealed after a correct password.
- User management cannot create Admins, assign the Admin role, or edit/deactivate Admin accounts.
- Logout ends the session; all later service calls are denied.
- Only Librarians and Admins can import books (checked in `BookService`). Book files are only read
  from inside `data/books/`, so a tampered `books.csv` cannot point at other files.

## 13. CSV format

```csv
userId,name,username,email,passwordHash,role,status,mustChangePassword
1,Abhi,admin01,admin@example.com,pbkdf2$100000$...$...,ADMIN,ACTIVE,false
2,"Kumar, Ravi",ravi01,ravi@example.com,pbkdf2$100000$...$...,STUDENT,ACTIVE,true
```

- The first seven columns match the specification. The 8th column, `mustChangePassword`, supports
  "change temporary password on first login". Files with only seven columns are still read.
- Roles: `ADMIN`, `LIBRARIAN`, `STUDENT`, `TEACHER`. Status: `ACTIVE`, `INACTIVE`.
- Proper CSV quoting: commas, quotes (`""`) and line breaks inside fields are handled.
- Usernames and emails are unique (case-insensitive). New IDs are `highest id + 1`.
- Malformed rows are skipped with a warning but **written back unchanged** on the next save.
  Admin → System Statistics shows how many rows were skipped.
- Saves go through a temporary file and an atomic rename, so a crash cannot corrupt `users.csv`.
  Updating one user preserves all other records.
- Close the program before editing `users.csv` by hand.

## 14. Tests

Two self-contained test runners (no JUnit needed). Right-click each → **Run As → Java Application**.
They use temporary folders, so your real `data/` files are never touched.

| Test class | Result | Covers |
|---|---|---|
| `SmartLibraryTests` | `Passed: 21, Failed: 0` | Login, roles, CSV, users (list below) |
| `AdvancedAlgorithmsTests` | `Passed: 15, Failed: 0` | Edmonds-Karp (classic example, max flow = min cut on 2,000 random graphs), assignment and König vs brute force, reviewer service and permissions, exact VC vs brute force, 2-approx bound (and a case where it is exactly 2×), IS/clique reductions, Randomized QuickSort correctness and n²/2 vs 2n ln n, reservoir uniformity (chi-square), usage stream, parallel reduce and Blelloch scan vs sequential, Brent's bound |
| `DynamicProgrammingTests` | `Passed: 11, Failed: 0` | Levenshtein / Damerau vs full DP table on 20,000 random pairs, known values, "Did you mean?", Smith-Waterman / Needleman-Wunsch vs full table, reworded-copy detection, Optimal BST vs brute force over every tree shape, search log |
| `DocumentSimilarityTests` | `Passed: 12, Failed: 0` | SA-IS vs naive sort on 20,000 random texts, Kasai LCP vs direct comparison, suffix tree lookups and matching statistics vs brute force, identical = 100% / unrelated = 0%, sentence breaks, both methods agree, phrase lookup, headings, sample books |
| `StringMatchingTests` | `Passed: 12, Failed: 0` | All 4 algorithms vs `String.indexOf` on 20,000 random texts, overlapping matches, edge cases, LPS table, menu switch, two-step search, import validation, import permissions, file-path safety, sample books |

`SmartLibraryTests` covers:
missing CSV file / folder, first-run setup code (wrong code, reuse after setup), successful
login (username and email), incorrect password and unknown user, placeholder hashes, role restrictions
for Students and Teachers, Admin-role creation blocked, role taken from CSV, duplicate username/email,
input validation, inactive users (login and live session), logout, forced first-login password change,
admin password reset, hashing, CSV special characters, malformed-row preservation, legacy 7-column
files, and user search.

Command line (from the project folder):

```bash
javac -d bin $(find src test -name "*.java")
java -cp bin com.smartlibrary.SmartLibraryTests
java -cp bin com.smartlibrary.StringMatchingTests
java -cp bin com.smartlibrary.DocumentSimilarityTests
java -cp bin com.smartlibrary.DynamicProgrammingTests
java -cp bin com.smartlibrary.AdvancedAlgorithmsTests
java -cp bin com.smartlibrary.Main
```

## 15. Project structure

```text
SmartLibrary/
├── .project, .classpath                  Eclipse project files
├── data/users.csv                        User accounts (11 sample users, no Admin)
├── data/books.csv                        Book catalogue
├── data/books/*.txt                      Book texts (5 sample books)
├── data/search_log.csv                   Search counts (created by the first search)
├── data/reviewers.csv                    Reviewer expertise and conflicts (3 sample Teachers)
├── data/review_assignments.csv           Saved reviewer assignments (created when saved)
├── data/usage_log.csv                    Usage stream (created by the first search / open / compare)
├── README.md
├── src/com/smartlibrary/
│   ├── Main.java                         Starts the console app
│   ├── model/User.java                   Account data (toString hides the hash)
│   ├── model/AccountStatus.java          ACTIVE / INACTIVE
│   ├── model/Book.java                   Book details (text lives in data/books/)
│   ├── auth/Role.java                    ADMIN, LIBRARIAN, STUDENT, TEACHER
│   ├── auth/PasswordUtil.java            PBKDF2 hashing, verification, strength rules
│   ├── auth/AuthService.java             Login, logout, session checks
│   ├── auth/AuthException.java
│   ├── auth/InitialSetup.java            One-time setup code for the first Admin
│   ├── data/UserCSV.java                 CSV parsing, escaping, atomic read-modify-write
│   ├── data/DuplicateUserException.java
│   ├── data/BookCSV.java                 books.csv + .txt import and safe reading
│   ├── data/SearchLogCSV.java            search_log.csv keyword counts (Module 3)
│   ├── data/ReviewerCSV.java             reviewers.csv + review_assignments.csv (Module 4)
│   ├── data/UsageLogCSV.java             usage_log.csv, read back as a stream (Module 6)
│   ├── service/UserService.java          User management + permission checks
│   ├── service/ServiceException.java
│   ├── service/ReviewService.java        Reviewer network, assignment, König, profiles (Module 4)
│   ├── service/AnalyticsService.java     Conflict graph (Module 5), ranking / sampling / parallel stats (Module 6)
│   ├── service/BookService.java          Import, find book (step 1), search inside book (step 2)
│   ├── console/ConsoleIO.java            Prompts, menus, messages, tables
│   ├── console/InputClosedException.java
│   ├── console/ConsoleApp.java           Setup → login → role menu → logout flow
│   ├── console/InitialSetupConsole.java  First-run Admin creation
│   ├── console/LoginConsole.java         Common login prompt (no role choice)
│   ├── console/BaseDashboard.java        Menu loop, Account Settings, logout, session checks
│   ├── console/AdminDashboard.java
│   ├── console/LibrarianDashboard.java
│   ├── console/StudentDashboard.java
│   ├── console/TeacherDashboard.java
│   ├── console/ManageUsersConsole.java   Admin: list, search, create, update, activate/deactivate
│   ├── console/ChangePasswordConsole.java
│   ├── console/SearchConsole.java        View All Books + two-step search + algorithm menu
│   ├── console/ManageBooksConsole.java   List and import books
│   ├── console/CompareConsole.java       Compare Documents (Module 2 + alignment from Module 3)
│   ├── console/SearchStatsConsole.java   Search Statistics with the Optimal BST (Module 3)
│   ├── console/ReviewerConsole.java      Reviewer Assignment (Module 4)
│   ├── console/MyReviewsConsole.java     Teacher: My Review Assignments (Module 4)
│   ├── console/ConflictConsole.java      Document Conflict Analysis (Module 5)
│   ├── console/AnalyticsConsole.java     Library Analytics (Module 6)
│   └── dsa/
│       ├── StringMatcher.java            Common interface
│       ├── Algorithm.java                Enum: name, complexity, menu switch
│       ├── SingleKeywordMatcher.java     Base for one-pattern algorithms
│       ├── KmpMatcher.java
│       ├── RabinKarpMatcher.java
│       ├── ZAlgorithmMatcher.java
│       ├── AhoCorasickMatcher.java
│       ├── Match.java, MatchResult.java  Positions, comparisons, time
│       ├── TextUtil.java                 Case folding, keyword parsing, line lookup (binary search)
│       ├── SuffixArraySAIS.java          Module 2: SA-IS suffix array (+ naive sort for comparison)
│       ├── LcpKasai.java                 Module 2: Kasai LCP array
│       ├── SuffixTree.java               Module 2: Ukkonen suffix tree, lookups, matching statistics
│       ├── NormalizedText.java           Module 2: text prepared for comparison, maps back to lines
│       ├── ComparisonSession.java        Module 2: compares two books, finds phrases
│       ├── SimilarityMethod.java         Module 2: method enum + menu switch
│       ├── SimilarityResult.java         Module 2: common phrases, similarity %, timings
│       ├── EditDistance.java             Module 3: Levenshtein, Damerau-Levenshtein
│       ├── SpellingCorrector.java        Module 3: "Did you mean?"
│       ├── SequenceAlignment.java        Module 3: Needleman-Wunsch, Smith-Waterman (word level)
│       ├── OptimalBST.java               Module 3: Optimal Binary Search Tree
│       ├── MaxFlow.java                  Module 4: Edmonds-Karp maximum flow
│       ├── ReviewerAssignment.java       Module 4: assignment network, bipartite matching, König cover
│       ├── ConflictGraph.java            Module 5: vertex cover (exact, 2-approx), independent set, clique
│       ├── RandomizedQuickSort.java      Module 6: randomized (and first-pivot) QuickSort
│       ├── ReservoirSampling.java        Module 6: Algorithm R
│       ├── ParallelStats.java            Module 6: parallel reduce, Blelloch scan, Brent's bound
│       └── README.md                     Module 1 details
├── test/com/smartlibrary/SmartLibraryTests.java
├── test/com/smartlibrary/StringMatchingTests.java
├── test/com/smartlibrary/DocumentSimilarityTests.java
├── test/com/smartlibrary/DynamicProgrammingTests.java
└── test/com/smartlibrary/AdvancedAlgorithmsTests.java
```

## 16. Next phase

Borrowing (issue / return, due dates, overdue tracking), research papers and recommendations.
Optional extra algorithms not included: Dinic's max flow, SAT/3-SAT and the 3-SAT → CLIQUE
reduction, Miller-Rabin, Matrix-Chain Multiplication and Bitmask DP.
