import json, sqlite3, sys, pathlib

snap_path = pathlib.Path(sys.argv[1])
out_path = pathlib.Path(sys.argv[2])
snap = json.loads(snap_path.read_text(encoding="utf-8"))

con = sqlite3.connect(out_path)
con.executescript("""
CREATE TABLE IF NOT EXISTS runs(
  id TEXT PRIMARY KEY, started_at INTEGER, tick INTEGER, game_time INTEGER, frozen INTEGER
);
CREATE TABLE IF NOT EXISTS logs(
  id INTEGER PRIMARY KEY AUTOINCREMENT, run_id TEXT, time INTEGER, level TEXT,
  logger TEXT, thread TEXT, message TEXT, throwable TEXT
);
""")
run_id = snap.get("runId", snap_path.parent.name)
con.execute("INSERT OR REPLACE INTO runs(id,started_at,tick,game_time,frozen) VALUES (?,?,?,?,?)",
            (run_id, snap.get("startedAt"), snap.get("tick"), snap.get("gameTime"), 1 if snap.get("frozen") else 0))
for line in snap.get("logs", []):
    con.execute("""INSERT INTO logs(run_id,time,level,logger,thread,message,throwable)
                   VALUES (?,?,?,?,?,?,?)""",
                (run_id, line.get("time"), line.get("level"), line.get("logger"),
                 line.get("thread"), line.get("message"), line.get("throwable")))
con.commit()
print("DB:", out_path)
print("runs:", con.execute("select * from runs").fetchall())
print("levels:", con.execute("select level,count(*) from logs group by level order by 2 desc").fetchall())
print("last 6:")
for row in con.execute("select level,message from logs order by id desc limit 6"):
    print(" ", row[0], row[1][:120])
con.close()
