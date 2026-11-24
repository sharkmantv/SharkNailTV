from pathlib import Path
p=Path("app/src/main/java/com/example/xtreamtv/MainActivity.kt")
s=p.read_text()
def swap(a,b):
    global s
    assert s.count(a)==1, "Unexpected catalogue source: "+a[:100]
    s=s.replace(a,b,1)

# Read cached categories/entries immediately, even if they are older than the
# short network refresh interval. Retain account scoping and bounded storage.
anchor='    suspend fun categories(action:String):List<Pair<String,String>> = withContext(Dispatchers.IO) {'
swap(anchor,'''    internal suspend fun cachedCategories(action:String):List<Pair<String,String>>? = withContext(Dispatchers.IO) {
        categoryCache.get(action)?.takeIf { System.currentTimeMillis()-it.first in 0 until 86_400_000L }?.second
            ?: diskCache.read("cat:$action",86_400_000L)?.let { (stamp,arr) ->
                ProviderCache.decodeCategories(arr).also { categoryCache.put(action,stamp to it) }
            }
    }
    internal suspend fun cachedEntries(action:String,kind:String,categoryId:String?):List<Entry>? = withContext(Dispatchers.IO) {
        val key="$action:$kind:$categoryId"
        entryCache.get(key)?.takeIf { System.currentTimeMillis()-it.first in 0 until 86_400_000L }?.second
            ?: diskCache.read("entry:$key",86_400_000L)?.let { (stamp,arr) ->
                ProviderCache.decodeEntries(arr).also { entryCache.put(key,stamp to it) }
            }
    }
'''+anchor)
# Only warm category lists on an idle Home page; cancel requests on navigation.
anchor='    LaunchedEffect(api,catalogueKind,catalogueRetry) {'
swap(anchor,'''    LaunchedEffect(api,section,selected) {
        val a=api ?: return@LaunchedEffect
        if(section!="Home" || selected!=null) return@LaunchedEffect
        delay(1500L)
        for(action in listOf("get_vod_categories","get_series_categories")) {
            try { a.categories(action) }
            catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e }
        }
    }
'''+anchor)
swap('''        try { categories=a.categories(when(catalogueKind) { "live" -> "get_live_categories"; "movie" -> "get_vod_categories"; else -> "get_series_categories" }); categoryId=categories.firstOrNull()?.first ?: "" }
        catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error="Category request failed ('''+chr(36)+'''{e.javaClass.simpleName})" } finally { categoriesLoading=false }''','''        val action=when(catalogueKind) { "live" -> "get_live_categories"; "movie" -> "get_vod_categories"; else -> "get_series_categories" }
        try {
            if(catalogueKind!="live") {
                a.cachedCategories(action)?.takeIf { it.isNotEmpty() }?.let {
                    categories=it
                    categoryId=it.first().first
                    categoriesLoading=false
                }
            }
            val fresh=a.categories(action)
            categories=fresh
            if(categoryId==null || fresh.none { it.first==categoryId }) categoryId=fresh.firstOrNull()?.first ?: ""
        } catch(e:Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            if(categories.isEmpty()) error="Category request failed ("+e.javaClass.simpleName+")"
        } finally { categoriesLoading=false }''')
swap('''            val entries=a.entries(when(catalogueKind) { "live" -> "get_live_streams"; "movie" -> "get_vod_streams"; else -> "get_series" },catalogueKind,cat.ifBlank { null })
            when(catalogueKind) { "live" -> live=entries; "movie" -> movies=entries; else -> series=entries }''','''            val action=when(catalogueKind) { "live" -> "get_live_streams"; "movie" -> "get_vod_streams"; else -> "get_series" }
            val id=cat.ifBlank { null }
            if(catalogueKind!="live") {
                a.cachedEntries(action,catalogueKind,id)?.takeIf { it.isNotEmpty() }?.let {
                    if(catalogueKind=="movie") movies=it else series=it
                    entriesLoading=false
                }
            }
            val entries=a.entries(action,catalogueKind,id)
            when(catalogueKind) { "live" -> live=entries; "movie" -> movies=entries; else -> series=entries }''')
# Do not recreate row lists during unrelated recompositions.
swap('''                                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    V4PosterRow(tmdbKey,"",sectionEntries.filterIndexed { i,_ -> i%2==0 },cardWidth=width){openTitle(it)}
                                    if(sectionEntries.size>1) V4PosterRow(tmdbKey,"",sectionEntries.filterIndexed { i,_ -> i%2==1 },cardWidth=width){openTitle(it)}
                                }''','''                                val firstRow=remember(sectionEntries) { sectionEntries.filterIndexed { i,_ -> i%2==0 } }
                                val secondRow=remember(sectionEntries) { sectionEntries.filterIndexed { i,_ -> i%2==1 } }
                                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    V4PosterRow(tmdbKey,"",firstRow,cardWidth=width){openTitle(it)}
                                    if(secondRow.isNotEmpty()) V4PosterRow(tmdbKey,"",secondRow,cardWidth=width){openTitle(it)}
                                }''')
p.write_text(s)
print("Catalogue cache-first display, idle category warmup and stable poster rows applied.")
