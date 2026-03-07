package com.zaneschepke.wireguardautotunnel.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

class DatabaseCallback(private val databaseProvider: Lazy<AppDatabase>) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Timber.d("Database created, inserting default rows")
        db.execSQL("INSERT INTO proxy_settings DEFAULT VALUES")
        db.execSQL("INSERT INTO general_settings DEFAULT VALUES")
        db.execSQL("INSERT INTO auto_tunnel_settings DEFAULT VALUES")
        db.execSQL("INSERT INTO monitoring_settings DEFAULT VALUES")
        db.execSQL("INSERT INTO dns_settings DEFAULT VALUES")
    }
}
