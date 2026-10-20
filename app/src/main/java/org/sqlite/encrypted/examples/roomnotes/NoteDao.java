package org.sqlite.encrypted.examples.roomnotes;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    long insert(Note note);

    @Query("DELETE FROM notes WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM notes ORDER BY created_at DESC, id DESC")
    List<Note> list();

    @Query("SELECT * FROM notes "
            + "WHERE title LIKE '%' || :term || '%' OR body LIKE '%' || :term || '%' "
            + "ORDER BY created_at DESC, id DESC")
    List<Note> search(String term);
}
