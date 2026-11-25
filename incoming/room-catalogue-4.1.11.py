from pathlib import Path
root=Path("app/src/main/java/com/example/xtreamtv")
def swap(s,a,b):
    assert s.count(a)==1, "Unexpected catalogue source: "+a[:150]
    return s.replace(a,b,1)

# Room index is scoped to the current catalogue snapshot, not credentials or other accounts.
# The existing disk cache remains the durable source and fallback; never duplicate it on disk.
(root/"CataloguePaging.kt").write_text('''package com.example.xtreamtv

import androidx.room.*
import androidx.paging.*
import androidx.paging.compose.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName="catalogue_rows", indices=[Index(value=["title"])])
data class CatalogueRow(
    @PrimaryKey @ColumnInfo(name="rowid") val rowid:Int,
    val id:String,val title:String,val image:String,val category:String,val kind:String,
    val container:String,val backdrop:String,val year:String,val rating:String,val genre:String,val overview:String
) {
    fun entry()=Entry(id,title,image,category,kind,container,backdrop,year,rating,genre,overview)
}
@Fts4(contentEntity=CatalogueRow::class)
@Entity(tableName="catalogue_fts")
data class CatalogueFts(val title:String)

@Dao
interface CatalogueDao {
    @Query("DELETE FROM catalogue_rows") suspend fun clear()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insert(rows:List<CatalogueRow>)
    @Transaction suspend fun replace(rows:List<CatalogueRow>) {
        clear()
        rows.chunked(500).forEach { insert(it) }
    }
    @Query("SELECT * FROM catalogue_rows ORDER BY rowid")
    fun providerOrder():PagingSource<Int,CatalogueRow>
    @Query("SELECT * FROM catalogue_rows ORDER BY title COLLATE NOCASE, rowid")
    fun titleOrder():PagingSource<Int,CatalogueRow>
    @Query("SELECT * FROM catalogue_rows WHERE rowid IN (SELECT rowid FROM catalogue_fts WHERE catalogue_fts MATCH :term) ORDER BY rowid")
    fun searchProvider(term:String):PagingSource<Int,CatalogueRow>
    @Query("SELECT * FROM catalogue_rows WHERE rowid IN (SELECT rowid FROM catalogue_fts WHERE catalogue_fts MATCH :term) ORDER BY title COLLATE NOCASE, rowid")
    fun searchTitle(term:String):PagingSource<Int,CatalogueRow>
}
@Database(entities=[CatalogueRow::class,CatalogueFts::class],version=1,exportSchema=false)
abstract class CatalogueDatabase:RoomDatabase() {
    abstract fun catalogue():CatalogueDao
}

@Composable internal fun cataloguePages(entries:List<Entry>, query:String, alphabetical:Boolean):LazyPagingItems<Entry> {
    val context=LocalContext.current.applicationContext
    val db=remember(entries,context) {
        runCatching { Room.inMemoryDatabaseBuilder(context,CatalogueDatabase::class.java).build() }.getOrNull()
    }
    var indexed by remember(db) { mutableStateOf(false) }
    DisposableEffect(db) { onDispose { db?.close() } }
    LaunchedEffect(db,entries) {
        if(db!=null) {
            try {
                withContext(Dispatchers.IO) {
                    db.catalogue().replace(entries.mapIndexed { i,e -> CatalogueRow(i+1,e.id,e.title,e.image,e.category,e.kind,e.container,e.backdrop,e.year,e.rating,e.genre,e.overview) })
                }
                indexed=true
            } catch(e:Exception) {
                if(e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("SharkCatalogue","Index unavailable; using original catalogue")
            }
        }
    }
    val term=remember(query) {
        query.split(Regex("[^\\\\p{L}\\\\p{N}]+")).filter { it.isNotBlank() }.joinToString(" ") { it+"*" }
    }
    val fallback=remember(entries,query,alphabetical,indexed) {
        if(indexed && term.isNotEmpty() || indexed && query.isBlank()) emptyList()
        else entries.filter { query.isBlank() || it.title.contains(query,true) }.let {
            if(alphabetical) it.sortedBy { e -> e.title.lowercase(java.util.Locale.ROOT) } else it
        }
    }
    val flow=remember(db,indexed,term,query,alphabetical,fallback) {
        if(db==null || !indexed || (term.isEmpty() && query.isNotBlank())) flowOf(PagingData.from(fallback))
        else Pager(PagingConfig(pageSize=40,initialLoadSize=80,prefetchDistance=12,enablePlaceholders=true,maxSize=240)) {
            val dao=db.catalogue()
            when {
                term.isEmpty() && alphabetical -> dao.titleOrder()
                term.isEmpty() -> dao.providerOrder()
                alphabetical -> dao.searchTitle(term)
                else -> dao.searchProvider(term)
            }
        }.flow.let { source -> kotlinx.coroutines.flow.map(source) { page -> page.map { it.entry() } } }
    }
    return flow.collectAsLazyPagingItems()
}
'''.replace('import kotlinx.coroutines.flow.flowOf','import kotlinx.coroutines.flow.flowOf\nimport kotlinx.coroutines.flow.map').replace('kotlinx.coroutines.flow.map(source) { page -> page.map { it.entry() } }','source.map { page -> page.map { it.entry() } }'))

p=root/"MainActivity.kt"
s=p.read_text()
start=s.index('@Composable private fun TvCatalogueBrowser(')
end=s.index('@Composable private fun V4PosterRow(',start)
ui=s[start:end]
a=ui.index('    val visible=remember(unique,query,alphabetical)')
b=ui.index('    val categoryState=',a)
ui=ui[:a]+'''    var appliedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) { if(query.isNotEmpty()) delay(180L); appliedQuery=query }
    val paged=cataloguePages(unique,appliedQuery,alphabetical)
'''+ui[b:]
ui=ui.replace('visible.isNotEmpty()','paged.itemCount>0').replace('visible.isEmpty()','paged.itemCount==0').replace('visible.size','paged.itemCount')
# Preserve stable provider IDs when loaded, and unique placeholder keys.
ui=swap(ui,'gridItems(visible,key={it.kind+":"+it.id}) { entry ->','''items(count=paged.itemCount,key={ index -> paged.peek(index)?.let { it.kind+":"+it.id } ?: "placeholder:"+index }) { index ->
                val entry=paged[index]
                if(entry!=null) {''')
anchor='''                              }
                          }
                          if(paged.itemCount==0)'''
# Close the nullable-entry branch before closing the item lambda.
# Match the actual source via indentation-independent local regex.
import re
m=re.search(r'(\n[ ]*\}\n[ ]*\}\n)([ ]*if\(paged.itemCount==0\))',ui)
assert m, "Could not find poster grid end"
ui=ui[:m.start()]+'\n                            } else { Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).background(Color(0xFF101E33))) }'+ui[m.start():]
# Independent implementation of the reference APK's fixed focus lanes and lightweight title information.
ui=swap(ui,'var focusedTitle by remember(categoryId) { mutableStateOf("") }','var focusedEntry by remember(categoryId) { mutableStateOf<Entry?>(null) }')
ui=swap(ui,'if(focused)focusedTitle=entry.title','if(focused)focusedEntry=entry')
ui=swap(ui,'Text(focusedTitle.ifBlank { uniqueCategories.firstOrNull { it.first==categoryId }?.second.orEmpty() },','Text(focusedEntry?.let { e -> listOf(e.title,e.year,e.genre,e.rating.takeIf { it.isNotBlank() }?.let { "★ "+it }.orEmpty()).filter { it.isNotBlank() }.joinToString("  ·  ") } ?: uniqueCategories.firstOrNull { it.first==categoryId }?.second.orEmpty(),')
ui=swap(ui,'LazyColumn(state=categoryState,modifier=Modifier.weight(1f).fillMaxWidth().clipToBounds(),','LazyColumn(state=categoryState,modifier=Modifier.weight(1f).fillMaxWidth().clipToBounds().focusRestorer(),')
ui=swap(ui,'modifier=Modifier.fillMaxSize().clipToBounds(),\n', 'modifier=Modifier.fillMaxSize().clipToBounds().focusRestorer(),\n')
ui=swap(ui,'LaunchedEffect(categoryId,paged.itemCount>0) { if(paged.itemCount>0) gridState.scrollToItem(0) }','LaunchedEffect(categoryId,appliedQuery,alphabetical) { gridState.scrollToItem(0) }')
if 'import androidx.compose.ui.focus.focusRestorer' not in s:
    s=s.replace('import androidx.compose.ui.focus.onFocusChanged','import androidx.compose.ui.focus.onFocusChanged\nimport androidx.compose.ui.focus.focusRestorer',1)
    # Import insertion shifts the previously calculated source offsets.
    start=s.index('@Composable private fun TvCatalogueBrowser(')
    end=s.index('@Composable private fun V4PosterRow(',start)

s=s[:start]+ui+s[end:]
p.write_text(s)
p=Path("app/build.gradle.kts")
s=p.read_text()
s=swap(s,'plugins {','plugins {\n    id("com.google.devtools.ksp") version "2.3.4"')
s=swap(s,'dependencies {','''dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.paging:paging-compose:3.3.6")
''')
p.write_text(s)
print("Room FTS catalogue index and Paging 3 grid applied, with original catalogue fallback.")
