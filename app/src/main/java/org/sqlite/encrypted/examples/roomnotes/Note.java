package org.sqlite.encrypted.examples.roomnotes;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public final class Note {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public final String title;
    public final String body;

    @ColumnInfo(name = "created_at")
    public final long createdAt;

    public Note(String title, String body, long createdAt) {
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return title + "\n" + body;
    }
}
