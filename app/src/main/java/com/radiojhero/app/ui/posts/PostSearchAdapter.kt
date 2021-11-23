package com.radiojhero.app.ui.posts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.radiojhero.app.R
import com.radiojhero.app.RoundedOutlineProvider
import com.radiojhero.app.fetchers.PostsFetcher

class PostSearchAdapter(private val listener: (post: PostsFetcher.Post) -> Unit) :
    RecyclerView.Adapter<PostSearchAdapter.ViewHolder>() {

    abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(post: PostsFetcher.Post, onClick: (post: PostsFetcher.Post) -> Unit)
    }

    class PostHitViewHolder(view: View) : ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        private val titleLabel: TextView = view.findViewById(R.id.title_label)
        private val excerptLabel: TextView = view.findViewById(R.id.excerpt_label)

        override fun bind(post: PostsFetcher.Post, onClick: (post: PostsFetcher.Post) -> Unit) {
            titleLabel.text = HtmlCompat.fromHtml(post.title, HtmlCompat.FROM_HTML_MODE_LEGACY)
            excerptLabel.text =
                HtmlCompat.fromHtml(post.subtitle, HtmlCompat.FROM_HTML_MODE_LEGACY)
            Glide.with(itemView).load(post.coverImage).into(thumbnail)

            itemView.setOnClickListener {
                onClick(post)
            }
        }
    }

    class HeaderViewHolder(view: View) : ViewHolder(view) {
        private val statsLabel: TextView = view.findViewById(R.id.stats_label)

        override fun bind(post: PostsFetcher.Post, onClick: (post: PostsFetcher.Post) -> Unit) {
            statsLabel.apply {
                text = resources.getQuantityString(
                    R.plurals.search_stats,
                    post.title.toInt(),
                    post.title.toInt(),
                    post.subtitle.toLong(),
                )
            }
        }
    }

    private var dataSet = mutableListOf<PostsFetcher.Post>()
    private var nbHits = 0
    private var processingTimeMS = 0L

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) -1 else 0
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        if (viewType == -1) {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.search_header, viewGroup, false)
            return HeaderViewHolder(view)
        }

        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.search_hit, viewGroup, false)

        view.findViewById<View>(R.id.thumbnail_wrapper).apply {
            clipToOutline = true
            outlineProvider = RoundedOutlineProvider(5f)
        }

        return PostHitViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = when (position) {
            0 -> PostsFetcher.Post(
                title = nbHits.toString(),
                subtitle = processingTimeMS.toString(),
            )
            else -> dataSet[position - 1]
        }

        viewHolder.bind(item, listener)
    }

    override fun getItemCount() = dataSet.size + 1

    fun append(posts: MutableList<PostsFetcher.Post>) {
        val previousItemCount = itemCount
        dataSet.addAll(posts)
        notifyItemRangeInserted(previousItemCount, posts.size)
    }

    fun replace(posts: MutableList<PostsFetcher.Post>) {
        val previousItemCount = itemCount
        dataSet = posts
        notifyItemChanged(0)

        if (posts.size + 1 > previousItemCount) {
            notifyItemRangeInserted(previousItemCount, posts.size - previousItemCount - 1)
            notifyItemRangeChanged(1, previousItemCount)
        } else {
            notifyItemRangeRemoved(posts.size + 1, previousItemCount - 1 - posts.size)
            notifyItemRangeChanged(1, posts.size)
        }
    }

    fun setStats(nbHits: Int, processingTimeMS: Long) {
        this.nbHits = nbHits
        this.processingTimeMS = processingTimeMS
    }
}