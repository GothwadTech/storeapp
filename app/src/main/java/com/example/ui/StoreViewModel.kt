package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

data class CatalogFilter(val kind: String? = null, val category: String? = null,
                         val query: String = "", val revision: Int = 0)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StoreViewModel(private val repository: StoreRepository = StoreNetwork.repository) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter())
    val catalog: StateFlow<LoadState<List<StoreApp>>> = filter
        .debounce { if (it.query.isBlank()) 0L else 300L }
        .flatMapLatest { f -> flow<LoadState<List<StoreApp>>> {
            emit(LoadState.Loading)
            emit(load { repository.catalog(f.kind, f.category, f.query) })
        } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private val _categories = MutableStateFlow<LoadState<List<StoreCategory>>>(LoadState.Loading)
    val categories = _categories.asStateFlow()
    private val _detail = MutableStateFlow<LoadState<AppDetailResponse>>(LoadState.Loading)
    val detail = _detail.asStateFlow()
    private var detailJob: kotlinx.coroutines.Job? = null

    init { refreshCategories() }
    fun setFilter(kind: String?, category: String?, query: String) {
        filter.value = CatalogFilter(kind, category, query, filter.value.revision)
    }
    fun retry() { filter.update { it.copy(revision = it.revision + 1) } }
    fun refreshCategories() { viewModelScope.launch {
        _categories.value = LoadState.Loading
        _categories.value = load { repository.categories() }
    } }
    fun openApp(id: String) {
        detailJob?.cancel()
        _detail.value = LoadState.Loading
        detailJob = viewModelScope.launch { _detail.value = load { repository.detail(id) } }
    }
    private suspend fun <T> load(block: suspend () -> T): LoadState<T> = try {
        LoadState.Ready(block())
    } catch (e: CancellationException) { throw e
    } catch (e: HttpException) {
        LoadState.Failed(when (e.code()) {
            404 -> "This app is no longer available."
            503 -> "The Store service is not configured or is temporarily unavailable."
            else -> "The Store request failed (${e.code()}). Please try again."
        })
    } catch (e: IOException) {
        LoadState.Failed("Could not reach the Store. Check your connection and try again.")
    } catch (e: Exception) {
        LoadState.Failed(if (e is IllegalStateException) e.message ?: "Store unavailable."
            else "Could not read the Store response. Please try again.")
    }
}
