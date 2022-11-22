package com.radiojhero.app.ui.posts

import android.app.SearchManager
import android.content.Context.SEARCH_SERVICE
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.text.trimmedLength
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.algolia.search.client.ClientSearch
import com.algolia.search.client.Index
import com.algolia.search.dsl.attributesToHighlight
import com.algolia.search.dsl.attributesToRetrieve
import com.algolia.search.dsl.attributesToSnippet
import com.algolia.search.dsl.query
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName
import com.algolia.search.model.response.ResponseSearch
import com.algolia.search.model.search.IgnorePlurals
import com.radiojhero.app.R
import com.radiojhero.app.databinding.FragmentPostsBinding
import com.radiojhero.app.endEditing
import com.radiojhero.app.fetchers.ConfigFetcher
import com.radiojhero.app.fetchers.PostsFetcher
import koleton.api.hideSkeleton
import koleton.api.loadSkeleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max


class PostsFragment : Fragment() {

    private var binding: FragmentPostsBinding? = null
    private lateinit var mAdapter: PostsAdapter
    private lateinit var mLayoutManager: GridLayoutManager
    private lateinit var mSearchAdapter: PostSearchAdapter
    private lateinit var mSearchLayoutManager: LinearLayoutManager
    private lateinit var mIndex: Index
    val viewModel: PostsViewModel by viewModels()

    private var search = ""
        set(value) {
            field = value
            searchPage = 0
        }
    private var searchRemainingHits = 0
    private var searchPage = 0
        set(value) {
            field = value
            doSearch()
        }
    private var isSearching = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val inflated = FragmentPostsBinding.inflate(inflater, container, false)
        binding = inflated
        setupRecycler()
        setupSearchRecycler()

        inflated.postsView.setOnRefreshListener {
            viewModel.fetch(true)
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.uiState.collect {
                    mAdapter.replace(it)

                    if (it.isEmpty()) {
                        inflated.recyclerView.loadSkeleton(R.layout.post_item) {
                            color(R.color.skeleton)
                            itemCount(60)
                        }
                        viewModel.fetch(true)
                    } else {
                        inflated.recyclerView.hideSkeleton()
                        inflated.postsView.isRefreshing = false
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.hasError.collect {
                    if (it && mAdapter.itemCount == 0) {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.error)
                            .setMessage(R.string.posts_error)
                            .setPositiveButton(R.string.button_dismiss, null)
                            .show()
                        inflated.recyclerView.hideSkeleton()
                        inflated.postsView.isRefreshing = false
                    }
                }
            }
        }

        return inflated.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                createMenu(menu, menuInflater)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return selectMenu(item)
            }
        }, viewLifecycleOwner, Lifecycle.State.STARTED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun createMenu(menu: Menu, inflater: MenuInflater) {
        val binding = this.binding ?: return
        inflater.inflate(R.menu.articles_menu, menu)
        val searchView =
            menu.findItem(R.id.action_search).actionView as SearchView
        val searchManager = requireActivity().getSystemService(SEARCH_SERVICE) as SearchManager?
        searchView.setSearchableInfo(searchManager!!.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    activity?.endEditing()
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    search = newText ?: ""
                    binding.apply {
                        if (search.trimmedLength() > 0) {
                            postsView.visibility = View.GONE
                            searchRecyclerView.visibility = View.VISIBLE
                        } else {
                            postsView.visibility = View.VISIBLE
                            searchRecyclerView.visibility = View.GONE
                        }
                    }
                    return true
                }
            }
        )
    }

    private fun selectMenu(item: MenuItem): Boolean = item.itemId == R.id.action_search

    private fun setupRecycler() {
        val binding = this.binding ?: return
        val columns =
            max(1f, resources.displayMetrics.widthPixels / resources.displayMetrics.density / 320f)
        mLayoutManager = GridLayoutManager(requireActivity(), columns.toInt())
        binding.recyclerView.layoutManager = mLayoutManager
        mAdapter = PostsAdapter {
            val action =
                PostsFragmentDirections.actionNavigationArticlesToNavigationWebpage(it.link)
            activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
        }
        mAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.recyclerView.adapter = mAdapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    activity?.endEditing()
                }

                if (dy <= 0) {
                    return
                }

                val visibleItemCount = mLayoutManager.childCount
                val totalItemCount = mLayoutManager.itemCount
                val pastVisibleItems = mLayoutManager.findFirstVisibleItemPosition()

                if (visibleItemCount + pastVisibleItems >= totalItemCount - 10) {
                    viewModel.fetch(false)
                }
            }
        })
    }

    private fun setupSearchRecycler() {
        val binding = this.binding ?: return
        val client = ClientSearch(
            applicationID = ApplicationID(ConfigFetcher.getConfig("algoliaApp")!!),
            apiKey = APIKey(ConfigFetcher.getConfig("algoliaKey")!!)
        )
        mIndex = client.initIndex(IndexName("wp_searchable_posts"))

        mSearchLayoutManager = LinearLayoutManager(requireActivity())
        binding.searchRecyclerView.layoutManager = mSearchLayoutManager
        mSearchAdapter = PostSearchAdapter {
            val action =
                PostsFragmentDirections.actionNavigationArticlesToNavigationWebpage(it.link)
            activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
        }
        mSearchAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.searchRecyclerView.adapter = mSearchAdapter
        binding.searchRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    activity?.endEditing()
                }

                if (dy <= 0) {
                    return
                }

                val visibleItemCount = mSearchLayoutManager.childCount
                val totalItemCount = mSearchLayoutManager.itemCount
                val pastVisibleItems = mSearchLayoutManager.findFirstVisibleItemPosition()

                if (visibleItemCount + pastVisibleItems >= totalItemCount - 10) {
                    searchPage++
                }
            }
        })
    }

    private fun doSearch() = lifecycleScope.launch {
        if (search.trimmedLength() == 0 || searchPage > 0 && (searchRemainingHits == 0 || isSearching)) {
            return@launch
        }

        isSearching = true
        val query = query {
            query = search
            filters = "post_type:post OR post_type:page"
            page = searchPage
            ignorePlurals = IgnorePlurals.True
            advancedSyntax = true
            hitsPerPage = 60
            highlightPreTag = "<mark>"
            highlightPostTag = "</mark>"
            snippetEllipsisText = "…"
            attributesToRetrieve {
                +"post_title"
                +"content"
                +"permalink"
                +"images"
            }
            attributesToHighlight {
                +"post_title"
                +"content"
            }
            attributesToSnippet {
                +"content"
            }
        }

        val response = withContext(Dispatchers.IO) {
            mIndex.search(query)
        }
        if (response.query != search) {
            return@launch
        }

        searchRemainingHits = max(0, response.nbHits - (searchPage + 1) * 60)
        val hits = deserialize(response.hits)

        activity?.runOnUiThread {
            if (searchPage > 0) {
                mSearchAdapter.append(hits)
            } else {
                mSearchAdapter.replace(hits)
                mSearchAdapter.setStats(response.nbHits, response.processingTimeMS)
            }
        }

        isSearching = false
    }

    private fun deserialize(hits: List<ResponseSearch.Hit>) = hits.map {
        val title =
            it.highlightResult["post_title"]!!.jsonObject["value"]!!.jsonPrimitive.content
        val excerpt = it.snippetResult["content"]!!.jsonObject["value"]!!.jsonPrimitive.content
        val permalink = it.json["permalink"]!!.jsonPrimitive.content.replace("wp.", "")
        val thumbnail = try {
            it.json["images"]?.jsonObject?.get("thumbnail")?.jsonObject?.get("url")?.jsonPrimitive?.content
                ?: ""
        } catch (error: Throwable) {
            ""
        }

        PostsFetcher.Post(
            title = title,
            subtitle = excerpt,
            link = permalink,
            coverImage = thumbnail,
        )
    }
}