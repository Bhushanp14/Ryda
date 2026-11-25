import sqlite3

db_path = "/data/user/0/com.ryda.app/databases/ryda_offline.db"
conn = sqlite3.connect(db_path)
cursor = conn.cursor()
ids = [
    "a7af8ef7-6dc4-43c8-a716-8a4a9a1607bb",
    "312a59e4-17a3-48de-a0a2-9ce20e82eca7",
    "cd350009-50a2-4d7c-95cc-fe2d5887390d"
]
cursor.execute(f"UPDATE rides SET sync_status = 'LOCAL_ONLY' WHERE id IN ({','.join(['?']*len(ids))})", ids)
conn.commit()
print("Updated rows:", cursor.rowcount)
conn.close()
