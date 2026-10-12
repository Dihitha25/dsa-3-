import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Research Connect backend. NO database: the arXiv CSV is streamed with BufferedReader,
 *  saved papers (data/saved.txt) and search history (data/history.txt) are plain text files. */
public class ResearchConnect {
    static class Paper { String id, t, c, s, lt, la, ls; int y; String[] a; }
    static final List<Paper> P = new ArrayList<>();
    static final Map<String, Integer> ID = new HashMap<>(), V = new HashMap<>(), CAT = new TreeMap<>();
    static final Map<String, List<Integer>> AU = new HashMap<>(), IDX = new HashMap<>();
    static final Set<String> STOP = new HashSet<>(Arrays.asList(("the and for that with this are from which have been their can our using use based into also these such than more not but was were its has new one two may how between over only each other both under via show shown paper present approach results method study problem").split(" ")));
    static final Path DIR = Paths.get("data"), SAVED = DIR.resolve("saved.txt"), HIST = DIR.resolve("history.txt");
    static String analyticsCache;
    static List<Map.Entry<String, List<Integer>>> topList;

    // ---------- file handling: stream the CSV (quoted fields may span several lines) ----------
    static void load(String file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8), 1 << 16)) {
            br.readLine();
            StringBuilder cur = new StringBuilder(); List<String> f = new ArrayList<>(); boolean q = false; int c;
            while ((c = br.read()) != -1) {
                if (c == '"') q = !q;
                else if (c == ',' && !q) { f.add(cur.toString()); cur.setLength(0); }
                else if (c == '\n' && !q) { f.add(cur.toString()); cur.setLength(0); add(f); f.clear(); }
                else if (c != '\r') cur.append((char) c);
            }
        }
    }
    static String[] parseAuthors(String s) {
        s = s.trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        String[] r = s.split("['\"],\\s*['\"]");
        for (int i = 0; i < r.length; i++) r[i] = r[i].replaceAll("^['\"]|['\"]$", "").trim();
        return r;
    }
    static void add(List<String> f) {
        if (f.size() < 9) return;
        Paper p = new Paper(); p.id = f.get(0); p.t = f.get(1).replaceAll("\\s+", " ").trim(); p.c = f.get(2);
        String[] d = f.get(4).split("/"); int y;
        try { y = Integer.parseInt(d[d.length - 1].trim()); } catch (Exception ex) { return; }
        p.y = y < 100 ? (y > 26 ? 1900 + y : 2000 + y) : y;
        p.a = parseAuthors(f.get(6));
        String s = f.get(8).replaceAll("\\s+", " ").trim();
        p.ls = s.toLowerCase(); p.s = s.length() > 400 ? s.substring(0, 400) : s;
        p.lt = p.t.toLowerCase(); p.la = String.join(" ", p.a).toLowerCase();
        int i = P.size(); P.add(p); ID.put(p.id, i); CAT.merge(p.c, 1, Integer::sum);
        for (String a : p.a) if (!a.isEmpty()) AU.computeIfAbsent(a, k -> new ArrayList<>()).add(i);
        for (String w : new HashSet<>(Arrays.asList(p.lt.split("[^a-z]+"))))
            if (w.length() >= 3) { V.merge(w, 1, Integer::sum); if (w.length() >= 4 && !STOP.contains(w)) IDX.computeIfAbsent(w, k -> new ArrayList<>()).add(i); }
    }
    static List<String> lines(Path p) throws IOException { return Files.exists(p) ? Files.readAllLines(p, StandardCharsets.UTF_8) : new ArrayList<>(); }

    // ---------- pattern matching + edit distance ----------
    static boolean naive(String t, String p) {
        for (int i = 0; i <= t.length() - p.length(); i++) { int j = 0; while (j < p.length() && t.charAt(i + j) == p.charAt(j)) j++; if (j == p.length()) return true; }
        return false;
    }
    static boolean kmp(String t, String p) {
        int[] f = new int[p.length()];
        for (int i = 1, k = 0; i < p.length(); i++) { while (k > 0 && p.charAt(i) != p.charAt(k)) k = f[k - 1]; if (p.charAt(i) == p.charAt(k)) k++; f[i] = k; }
        for (int i = 0, k = 0; i < t.length(); i++) { while (k > 0 && t.charAt(i) != p.charAt(k)) k = f[k - 1]; if (t.charAt(i) == p.charAt(k)) k++; if (k == p.length()) return true; }
        return false;
    }
    static boolean rk(String t, String p) {
        int m = p.length(), n = t.length(); if (m > n) return false;
        final long M = 1000003; long h = 0, w = 0, pw = 1;
        for (int i = 0; i < m; i++) { h = (h * 256 + p.charAt(i)) % M; w = (w * 256 + t.charAt(i)) % M; if (i > 0) pw = pw * 256 % M; }
        for (int i = 0; ; i++) {
            if (h == w && t.startsWith(p, i)) return true;
            if (i + m >= n) return false;
            w = ((w - t.charAt(i) * pw % M + M) * 256 + t.charAt(i + m)) % M;
        }
    }
    static boolean z(String t, String p) {
        String s = p + "\u0001" + t; int n = s.length(), m = p.length(); int[] z = new int[n];
        for (int i = 1, l = 0, r = 0; i < n; i++) {
            if (i < r) z[i] = Math.min(r - i, z[i - l]);
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) z[i]++;
            if (i + z[i] > r) { l = i; r = i + z[i]; }
            if (z[i] == m) return true;
        }
        return false;
    }
    static boolean m(String alg, String t, String p) {
        switch (alg) { case "naive": return naive(t, p); case "rk": return rk(t, p); case "z": return z(t, p); default: return kmp(t, p); }
    }
    static int ed(String a, String b) {
        int[] d = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) d[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = d[0]; d[0] = i;
            for (int j = 1; j <= b.length(); j++) { int tmp = d[j]; d[j] = Math.min(Math.min(d[j] + 1, d[j - 1] + 1), prev + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)); prev = tmp; }
        }
        return d[b.length()];
    }
    static List<String> near(String t) {
        int k = t.length() > 5 ? 2 : 1; List<String> r = new ArrayList<>(List.of(t));
        r.addAll(V.entrySet().stream().filter(e -> !e.getKey().equals(t) && Math.abs(e.getKey().length() - t.length()) <= k && ed(t, e.getKey()) <= k)
            .sorted((x, z) -> z.getValue() - x.getValue()).limit(5).map(Map.Entry::getKey).collect(Collectors.toList()));
        return r;
    }
    static String dym(String t) {
        if (V.containsKey(t)) return t; String b = t; int bd = 3, bf = 0;
        for (Map.Entry<String, Integer> e : V.entrySet()) {
            String w = e.getKey();
            if (Math.abs(w.length() - t.length()) < 3) { int d = ed(t, w); if (d <= 2 && (d < bd || (d == bd && e.getValue() > bf))) { bd = d; b = w; bf = e.getValue(); } }
        }
        return b;
    }

    // ---------- JSON helpers ----------
    static String j(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) { if (c == '"' || c == '\\') b.append('\\').append(c); else if (c < 32) b.append(' '); else b.append(c); }
        return b.append('"').toString();
    }
    static String arr(Stream<String> s) { return s.collect(Collectors.joining(",", "[", "]")); }
    static String pair(String n, int v) { return "[" + j(n) + "," + v + "]"; }
    static String paperJson(Paper p, int m) {
        return "{\"id\":" + j(p.id) + ",\"t\":" + j(p.t) + ",\"c\":" + j(p.c) + ",\"y\":" + p.y + ",\"a\":" + arr(Arrays.stream(p.a).limit(8).map(ResearchConnect::j))
            + ",\"s\":" + j(p.s) + (m >= 0 ? ",\"m\":" + m : "") + "}";
    }

    // ---------- features ----------
    static String search(Map<String, String> q) throws IOException {
        final String qs = q.getOrDefault("q", "").toLowerCase().trim(), f = q.getOrDefault("field", "all"), cat = q.getOrDefault("cat", ""), alg = q.getOrDefault("alg", "kmp");
        int off = Integer.parseInt(q.getOrDefault("offset", "0"));
        final boolean inT = f.equals("all") || f.equals("t"), inA = f.equals("all") || f.equals("a"), inS = f.equals("all") || f.equals("s");
        String[] toks = qs.isEmpty() ? new String[0] : qs.split("\\s+");
        final String a2 = alg.equals("fuzzy") ? "kmp" : alg;
        final List<List<String>> alts = new ArrayList<>();
        for (String t : toks) alts.add(alg.equals("fuzzy") ? near(t) : List.of(t));
        if (off == 0 && !qs.isEmpty()) try (FileWriter fw = new FileWriter(HIST.toFile(), true)) { fw.write(qs + "\n"); }
        long t0 = System.nanoTime();
        List<Integer> hit = new ArrayList<>(IntStream.range(0, P.size()).parallel().filter(i -> {
            Paper p = P.get(i);
            if (!cat.isEmpty() && !p.c.equals(cat)) return false;
            for (List<String> al : alts) {
                boolean ok = false;
                for (String w : al) if ((inT && m(a2, p.lt, w)) || (inA && m(a2, p.la, w)) || (inS && m(a2, p.ls, w))) { ok = true; break; }
                if (!ok) return false;
            }
            return true;
        }).boxed().collect(Collectors.toList()));
        double ms = (System.nanoTime() - t0) / 1e6;
        String sort = q.getOrDefault("sort", "r");
        if (sort.equals("n")) hit.sort((x, z) -> P.get(z).y - P.get(x).y);
        if (sort.equals("o")) hit.sort((x, z) -> P.get(x).y - P.get(z).y);
        String did = hit.isEmpty() && toks.length > 0 ? Arrays.stream(toks).map(ResearchConnect::dym).collect(Collectors.joining(" ")) : "";
        StringBuilder sb = new StringBuilder("{\"total\":" + hit.size() + ",\"ms\":" + String.format(Locale.ROOT, "%.1f", ms) + ",\"did\":" + j(did) + ",\"res\":[");
        for (int i = off; i < Math.min(hit.size(), off + 15); i++) { if (i > off) sb.append(','); sb.append(paperJson(P.get(hit.get(i)), -1)); }
        return sb.append("]}").toString();
    }
    static String sg(String v, String k) { return "{\"v\":" + j(v) + ",\"k\":" + j(k) + "}"; }
    static String suggest(String raw) {
        String v = raw.toLowerCase().trim(); if (v.length() < 2) return "[]";
        String[] parts = v.split("\\s+"); String w = parts[parts.length - 1], pre = v.substring(0, v.length() - w.length());
        List<String> o = new ArrayList<>();
        List<String> kw = V.entrySet().stream().filter(e -> e.getKey().startsWith(w)).sorted((x, z) -> z.getValue() - x.getValue()).limit(5).map(e -> pre + e.getKey()).collect(Collectors.toList());
        for (String k : kw) o.add(sg(k, "keyword"));
        if (kw.isEmpty() && w.length() > 2) { String d = dym(w); if (!d.equals(w)) o.add(sg(pre + d, "did you mean")); }
        int n = 0; for (Paper p : P) if (p.lt.contains(v)) { o.add(sg(p.t, "paper")); if (++n == 4) break; }
        n = 0; for (String a : AU.keySet()) if (a.toLowerCase().contains(v)) { o.add(sg(a, "author")); if (++n == 3) break; }
        return arr(o.stream());
    }
    /** Recommendations: inverted index on title words (idf-weighted) + shared authors + same domain. */
    static String recs(String ids, int k) {
        Set<Integer> seeds = new HashSet<>(); for (String s : ids.split(",")) if (ID.containsKey(s)) seeds.add(ID.get(s));
        if (seeds.isEmpty()) return "[]";
        Map<Integer, Double> sc = new HashMap<>(); Set<String> cats = new HashSet<>();
        for (int s : seeds) {
            Paper p = P.get(s); cats.add(p.c);
            for (String w : new HashSet<>(Arrays.asList(p.lt.split("[^a-z]+")))) { List<Integer> l = IDX.get(w); if (l != null && l.size() < 5000) { double wt = 1 / Math.log(2 + l.size()); for (int x : l) sc.merge(x, wt, Double::sum); } }
            for (String a : p.a) { List<Integer> l = AU.get(a); if (l != null) for (int x : l) sc.merge(x, 2.0, Double::sum); }
        }
        sc.replaceAll((x, v) -> v + (cats.contains(P.get(x).c) ? .5 : 0));
        List<Map.Entry<Integer, Double>> top = sc.entrySet().stream().filter(e -> !seeds.contains(e.getKey())).sorted((x, z) -> Double.compare(z.getValue(), x.getValue())).limit(k).collect(Collectors.toList());
        if (top.isEmpty()) return "[]";
        double mx = top.get(0).getValue();
        return arr(top.stream().map(e -> paperJson(P.get(e.getKey()), Math.min(99, (int) (e.getValue() / mx * 100)))));
    }
    static Map<String, Integer> collabs(String n) {
        Map<String, Integer> m = new HashMap<>();
        for (int i : AU.get(n)) for (String b : P.get(i).a) if (!b.equals(n) && !b.isEmpty()) m.merge(b, 1, Integer::sum);
        return m;
    }
    static synchronized List<Map.Entry<String, List<Integer>>> top() {
        if (topList == null) topList = AU.entrySet().stream().sorted((x, z) -> z.getValue().size() - x.getValue().size()).limit(25).collect(Collectors.toList());
        return topList;
    }
    static String apiAuthors(Map<String, String> q) {
        String f = q.getOrDefault("q", "").toLowerCase().trim();
        List<Map.Entry<String, List<Integer>>> l = f.isEmpty() ? top() : AU.entrySet().stream().filter(e -> e.getKey().toLowerCase().contains(f))
            .sorted((x, z) -> z.getValue().size() - x.getValue().size()).limit(25).collect(Collectors.toList());
        return arr(l.stream().map(e -> "{\"n\":" + j(e.getKey()) + ",\"p\":" + e.getValue().size() + ",\"c\":" + collabs(e.getKey()).size() + "}"));
    }
    static String author(String n) {
        List<Integer> l = AU.get(n); if (l == null) return "{}";
        Map<String, Integer> c = collabs(n);
        return "{\"n\":" + j(n) + ",\"c\":" + c.size() + ",\"papers\":" + arr(l.stream().map(i -> "{\"t\":" + j(P.get(i).t) + ",\"y\":" + P.get(i).y + "}"))
            + ",\"collabs\":" + arr(c.entrySet().stream().sorted((x, z) -> z.getValue() - x.getValue()).limit(15).map(e -> pair(e.getKey(), e.getValue()))) + "}";
    }
    static synchronized String analytics() {
        if (analyticsCache == null) {
            String dom = arr(CAT.entrySet().stream().sorted((x, z) -> z.getValue() - x.getValue()).limit(12).map(e -> pair(e.getKey(), e.getValue())));
            Map<Integer, Integer> ym = new TreeMap<>(); for (Paper p : P) ym.merge(p.y, 1, Integer::sum);
            List<Map.Entry<Integer, Integer>> yl = new ArrayList<>(ym.entrySet());
            String yrs = arr(yl.subList(Math.max(0, yl.size() - 14), yl.size()).stream().map(e -> pair("" + e.getKey(), e.getValue())));
            String auth = arr(top().stream().limit(10).map(e -> pair(e.getKey(), e.getValue().size() + collabs(e.getKey()).size())));
            String kws = arr(V.entrySet().stream().filter(e -> e.getKey().length() > 4 && !STOP.contains(e.getKey())).sorted((x, z) -> z.getValue() - x.getValue()).limit(12).map(e -> pair(e.getKey(), e.getValue())));
            analyticsCache = "{\"d\":" + dom + ",\"y\":" + yrs + ",\"a\":" + auth + ",\"k\":" + kws + "}";
        }
        return analyticsCache;
    }
    static synchronized String toggle(String id) throws IOException {
        List<String> l = lines(SAVED); boolean on = !l.remove(id); if (on) l.add(id);
        Files.write(SAVED, l, StandardCharsets.UTF_8); return "{\"saved\":" + on + "}";
    }
    static String api(String name, Map<String, String> q) throws IOException {
        switch (name) {
            case "meta": return "{\"total\":" + P.size() + ",\"authors\":" + AU.size() + ",\"cats\":" + arr(CAT.keySet().stream().map(ResearchConnect::j)) + "}";
            case "search": return search(q);
            case "suggest": return suggest(q.getOrDefault("q", ""));
            case "recs": return recs(q.getOrDefault("ids", ""), Integer.parseInt(q.getOrDefault("k", "8")));
            case "authors": return apiAuthors(q);
            case "author": return author(q.getOrDefault("name", ""));
            case "analytics": return analytics();
            case "save": return toggle(q.getOrDefault("id", ""));
            case "saved": return arr(lines(SAVED).stream().filter(ID::containsKey).map(i -> paperJson(P.get(ID.get(i)), -1)));
            case "history": { List<String> l = lines(HIST); Collections.reverse(l); return arr(l.stream().distinct().limit(6).map(ResearchConnect::j)); }
            default: return "{}";
        }
    }
    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>(); if (raw == null) return m;
        for (String kv : raw.split("&")) { String[] p = kv.split("=", 2); if (p.length == 2) m.put(p[0], URLDecoder.decode(p[1], StandardCharsets.UTF_8)); }
        return m;
    }

    public static void main(String[] a) throws Exception {
        String file = a.length > 0 ? a[0] : "data/arXiv_scientific_dataset.csv"; int port = a.length > 1 ? Integer.parseInt(a[1]) : 8080;
        Files.createDirectories(DIR);
        System.out.println("Loading " + file + " (about a minute for the full dataset)...");
        long t0 = System.currentTimeMillis(); load(file);
        System.out.println("Loaded " + P.size() + " papers in " + (System.currentTimeMillis() - t0) / 1000 + "s");
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        s.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath(); byte[] b; String type = "application/json";
                if (path.startsWith("/api/")) b = api(path.substring(5), query(ex.getRequestURI().getRawQuery())).getBytes(StandardCharsets.UTF_8);
                else {
                    Path f = Paths.get("web", path.equals("/") ? "index.html" : path.substring(1)).normalize();
                    if (!f.startsWith("web") || !Files.isRegularFile(f)) { ex.sendResponseHeaders(404, -1); return; }
                    b = Files.readAllBytes(f); type = path.endsWith(".js") ? "text/javascript" : "text/html";
                }
                ex.getResponseHeaders().add("Content-Type", type + "; charset=utf-8");
                ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b);
            } catch (Exception x) { x.printStackTrace(); try { ex.sendResponseHeaders(500, -1); } catch (Exception ignore) { } }
            finally { ex.close(); }
        });
        s.start();
        System.out.println("Open http://localhost:" + port);
    }
}
