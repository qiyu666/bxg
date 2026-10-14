package com.qiyu.safe.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地数据库（存钱保险柜）。数据仅保存在应用内部私有目录，无网络、无云端。 */
class VaultDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                email TEXT,
                pass_salt TEXT,
                pass_hash TEXT,
                totp_secret TEXT,
                goal_fen INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE deposits(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                amount_fen INTEGER NOT NULL,
                term TEXT NOT NULL,
                period_months INTEGER NOT NULL,
                start_ts INTEGER NOT NULL,
                mature_ts INTEGER NOT NULL,
                converted INTEGER NOT NULL DEFAULT 0,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE transactions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                amount_fen INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                ref_deposit INTEGER,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_deposits_user ON deposits(user_id)")
        db.execSQL("CREATE INDEX idx_txn_user ON transactions(user_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE users ADD COLUMN goal_fen INTEGER NOT NULL DEFAULT 0")
    }

    companion object {
        const val DB_NAME = "vault.db"
        const val DB_VERSION = 2
    }
}
