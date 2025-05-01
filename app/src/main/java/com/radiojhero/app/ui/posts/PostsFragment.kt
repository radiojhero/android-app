package com.radiojhero.app.ui.posts

import android.app.SearchManager
import android.content.Context.SEARCH_SERVICE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.algolia.client.api.SearchClient
import com.algolia.client.model.search.HighlightResultOption
import com.algolia.client.model.search.Hit
import com.algolia.client.model.search.IgnorePlurals
import com.algolia.client.model.search.SearchParamsObject
import com.algolia.client.model.search.SnippetResultOption
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.radiojhero.app.MainActivity
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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max


class PostsFragment : Fragment() {

    private var binding: FragmentPostsBinding? = null
    private lateinit var mAdapter: PostsAdapter
    private lateinit var mLayoutManager: GridLayoutManager
    private lateinit var mSearchAdapter: PostSearchAdapter
    private lateinit var mSearchLayoutManager: LinearLayoutManager
    private lateinit var mClient: SearchClient
    private val INDEX = "site_pages"
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
                            color(R.color.md_theme_surfaceVariant)
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
                        AlertDialog.Builder(requireContext()).setTitle(R.string.error)
                            .setMessage(R.string.posts_error)
                            .setPositiveButton(R.string.button_dismiss, null).show()
                        inflated.recyclerView.hideSkeleton()
                        inflated.postsView.isRefreshing = false
                    }
                }
            }
        }

        activity?.findViewById<BottomNavigationView>(R.id.nav_view)
            ?.setOnItemReselectedListener { item ->
                if (item.itemId == R.id.navigation_articles) {
                    inflated.recyclerView.smoothScrollToPosition(0)
                    inflated.searchRecyclerView.smoothScrollToPosition(0)
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
        binding?.recyclerView?.clearOnScrollListeners()
        binding?.searchRecyclerView?.clearOnScrollListeners()
        binding = null
    }

    private fun createMenu(menu: Menu, inflater: MenuInflater) {
        val binding = this.binding ?: return
        inflater.inflate(R.menu.articles_menu, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        val searchManager = requireActivity().getSystemService(SEARCH_SERVICE) as SearchManager?
        searchView.setSearchableInfo(searchManager!!.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
        })
    }

    private fun selectMenu(item: MenuItem): Boolean = item.itemId == R.id.action_search

    private fun setupRecycler() {
        val binding = this.binding ?: return
        val columns =
            max(1f, resources.displayMetrics.widthPixels / resources.displayMetrics.density / 320f)
        mLayoutManager = GridLayoutManager(requireActivity(), columns.toInt())
        binding.recyclerView.layoutManager = mLayoutManager
        mAdapter = PostsAdapter {
            val link = "https://${ConfigFetcher.getConfig("host")}${it.link}"
            val action = PostsFragmentDirections.actionNavigationArticlesToNavigationWebpage(link)
            activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
        }
        mAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.recyclerView.adapter = mAdapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                (requireActivity() as MainActivity).toggleAppBarBackground(recyclerView.computeVerticalScrollOffset() > 0)

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
        (requireActivity() as MainActivity).toggleAppBarBackground(binding.recyclerView.computeVerticalScrollOffset() > 0)
    }

    private fun setupSearchRecycler() {
        val binding = this.binding ?: return
        mClient = SearchClient(
            appId = ConfigFetcher.getConfig("algoliaApp")!!,
            apiKey = ConfigFetcher.getConfig("algoliaKey")!!
        )

        mSearchLayoutManager = LinearLayoutManager(requireActivity())
        binding.searchRecyclerView.layoutManager = mSearchLayoutManager
        mSearchAdapter = PostSearchAdapter {
            val link = "https://${ConfigFetcher.getConfig("host")}${it.link}"
            val action = PostsFragmentDirections.actionNavigationArticlesToNavigationWebpage(link)
            activity?.findNavController(R.id.nav_host_fragment_activity_main)?.navigate(action)
        }
        mSearchAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.searchRecyclerView.adapter = mSearchAdapter
        binding.searchRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                (requireActivity() as MainActivity).toggleAppBarBackground(recyclerView.computeVerticalScrollOffset() > 0)

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
        val query = SearchParamsObject(
            query = search,
            page = searchPage,
            ignorePlurals = IgnorePlurals.of(true),
            advancedSyntax = true,
            hitsPerPage = 60,
            highlightPreTag = "<mark>",
            highlightPostTag = "</mark>",
            snippetEllipsisText = "…",
            attributesToRetrieve = arrayListOf(
                "title",
                "body",
                "path",
                "cover",
            ),
            attributesToHighlight = arrayListOf(
                "title",
                "body",
            ),
            attributesToSnippet = arrayListOf(
                "body",
            ),
        )

        val response = withContext(Dispatchers.IO) {
            mClient.searchSingleIndex(INDEX, query)
        }
        if (response.query != search) {
            return@launch
        }

        searchRemainingHits = max(0, (response.nbHits ?: 0) - (searchPage + 1) * 60)
        val hits = deserialize(response.hits)

        activity?.runOnUiThread {
            if (searchPage > 0) {
                mSearchAdapter.append(hits)
            } else {
                mSearchAdapter.replace(hits)
                mSearchAdapter.setStats(response.nbHits ?: 0, response.processingTimeMS.toLong())
            }
        }

        isSearching = false
    }

    private fun deserialize(hits: List<Hit>) = hits.map {
        val title = (it.highlightResult?.get("title") as HighlightResultOption).value
        val excerpt = (it.snippetResult?.get("body") as SnippetResultOption).value
        val permalink = it.additionalProperties?.get("path")!!.jsonPrimitive.content
        val thumbnail = try {
            it.additionalProperties?.get("cover")?.jsonPrimitive?.content ?: ""
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