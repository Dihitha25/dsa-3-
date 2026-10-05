# Research Connect (Java, no database)

Search, recommendations, author network and analytics over the full arXiv dataset (287,421 papers).

## Run
1. Install JDK 17+ (https://adoptium.net) and the VS Code "Extension Pack for Java".
2. Copy `arXiv_scientific_dataset.csv` into the `data/` folder.
3. Open this folder in VS Code and press Run on `src/ResearchConnect.java` (or Run > Start Debugging),
   or in a terminal: `run.bat` (Windows) / `./run.sh` (Mac/Linux).
4. Wait for "Open http://localhost:8080" (the CSV load takes about a minute), then open that link.

Needs about 3 GB of free RAM (`-Xmx3g`). Lower it in run.bat / launch.json if needed (small CSV samples need far less).

## Structure
- `src/ResearchConnect.java`: server, CSV loader (BufferedReader), Naive/KMP/Rabin-Karp/Z search, Edit Distance fuzzy search, recommendations, author graph, analytics.
- `web/index.html`, `web/app.js`: the user interface.
- `data/`: the CSV, plus `saved.txt` (reading list) and `history.txt` (recent searches), written with Java file handling.

## Notes
- Fuzzy search on all 287k papers takes a few seconds. KMP/Z/Rabin-Karp take well under a second.
- The dataset has no citation links, so influence = papers + collaborators (co-authorship graph).
