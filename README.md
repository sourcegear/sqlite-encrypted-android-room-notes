# Encrypted Room Notes with SQLite SEE

This runnable Android application demonstrates an encrypted Room database using
`org.sqlite.encrypted:sqlite3` and the official SQLite Encryption Extension
(SEE). Room continues to provide entities, DAOs, migrations, and query
generation; `SeeOpenHelperFactory` connects Room to the SQLite.Encrypted Android
driver.

This example uses SQLite SEE. It does not use SQLCipher.

## What the application demonstrates

- Installing the published SQLite.Encrypted Android package from Maven.
- Activating SEE once before Room opens its database.
- Supplying Room with a custom `SupportSQLiteOpenHelper.Factory`.
- Using a keyed `SQLiteOpenHelper` so every connection in Room's WAL pool calls
  `key()` with the database key.
- Generating a random 256-bit database key.
- Protecting that database key with a non-exportable Android Keystore key.
- Inserting, searching, listing, and deleting encrypted notes through a normal
  Room DAO.

## Prerequisites

- Android Studio or a command-line Android SDK.
- Android API 23 or newer.
- Access to `https://packages.sourcegear.com/maven/releases`.
- A licensed SEE activation key.

Free fully-featured demo keys are available from
[sqlite.sourcegear.com](https://sqlite.sourcegear.com/#evaluation).

## Install the package

The sample's `settings.gradle` adds the SQLite.Encrypted Maven repository:

```groovy
maven {
    url = uri('https://packages.sourcegear.com/maven/releases')
}
```

The app consumes the published package at an exact version:

```groovy
implementation 'org.sqlite.encrypted:sqlite3:3.54.0'
```

## Configure the activation key

Put the machine-local value in the project's untracked `local.properties`
file at the sample root, next to `settings.gradle` (not inside `app`):

```properties
seeActivationKey=<your SEE activation key>
```

The same value can be supplied as an environment variable:

```powershell
$env:SQLITE_ENCRYPTED_ACTIVATION_KEY = '<your SEE activation key>'
```

The activation key becomes a `BuildConfig` value for this easy-to-run example.
That keeps it out of source control, but does not make it secret: values in an
Android application can be recovered from the APK. Choose an activation-key
delivery and application-protection strategy appropriate for your SEE license
and production threat model.

The sample rejects a missing, blank, placeholder, or incorrectly prefixed
activation key before opening a database. After calling `activateSEE`, it opens
a temporary keyed database in the application cache. If SEE rejects the key,
the application reports an activation-specific error before Room attempts to
open the notes database. Because SEE activation is process-global, completely
restart the application after correcting an invalid activation key.

## Build and run

Open this directory in Android Studio and run the `app` configuration. From a
terminal with Gradle installed, use:

```shell
gradle installDebug
```

Add a note, restart the application, and search for it. Long-press a note to
delete it.

## Room and SEE initialization

The important setup is in `MainActivity.java`:

```java
System.loadLibrary("sqliteX");
SQLiteDatabase.activateSEE(activationKey);

String databaseKey = new DatabaseKeyStore(context).getOrCreate();
NotesDatabase database = Room.databaseBuilder(
        context, NotesDatabase.class, "notes.db")
    .openHelperFactory(new SeeOpenHelperFactory(databaseKey))
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .build();
```

`activateSEE` must run before Room opens an encrypted database. The custom
factory passes the database key to its `SQLiteOpenHelper` delegate:

```java
new SQLiteOpenHelper(
        configuration.context,
        configuration.name,
        null,
        configuration.callback.version,
        databaseKey,
        null);
```

Room can use a connection pool in WAL mode. The keyed helper retains the key in
the database configuration and calls the native `key()` implementation whenever
it opens a connection, including later reader connections in the pool. A failed
key call or an unreadable schema makes the connection open fail.

After the database is built, the application uses ordinary Room calls:

```java
database.notes().insert(
    new Note("Trail conditions", "Creek crossing is high", now)
);
List<Note> matches = database.notes().search("creek");
```

## Database-key storage

`DatabaseKeyStore` creates a random 32-byte value and represents it as a
hexadecimal text key. The text is encrypted with AES-GCM using a non-exportable
key in Android Keystore. Only the encrypted value and its IV are stored in
private `SharedPreferences`.

Deleting application data removes both the database and its stored keys. A
production backup design must keep an encrypted database and the material
needed to recover its key together.

## Project layout

```text
app/src/main/java/.../MainActivity.java          SEE and Room initialization, UI workflow
app/src/main/java/.../SeeOpenHelperFactory.java  Room-to-SQLite.Encrypted adapter
app/src/main/java/.../DatabaseKeyStore.java      Random key and Android Keystore wrapping
app/src/main/java/.../NotesDatabase.java         Room database
app/src/main/java/.../Note.java                  Room entity
app/src/main/java/.../NoteDao.java               Room CRUD and search queries
app/src/main/res/layout/activity_main.xml         Example user interface
```
