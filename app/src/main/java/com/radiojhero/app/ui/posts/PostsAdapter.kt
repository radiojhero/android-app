package com.radiojhero.app.ui.posts

import android.text.format.DateFormat
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
import java.text.SimpleDateFormat
import java.util.*

class PostsAdapter(private val listener: (post: PostsFetcher.Post) -> Unit) :
    RecyclerView.Adapter<PostsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.image_view)
        private val titleLabel: TextView = view.findViewById(R.id.title_label)
        private val subtitleLabel: TextView = view.findViewById(R.id.subtitle_label)
        private val categoryLabel: TextView = view.findViewById(R.id.category_label)
        private val dateLabel: TextView = view.findViewById(R.id.date_label)

        fun bind(post: PostsFetcher.Post, onClick: (post: PostsFetcher.Post) -> Unit) {
            val formatPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
            val dateFormat = SimpleDateFormat(formatPattern, Locale.getDefault())

            titleLabel.text = HtmlCompat.fromHtml(post.title, HtmlCompat.FROM_HTML_MODE_LEGACY)
            subtitleLabel.text =
                HtmlCompat.fromHtml(post.subtitle, HtmlCompat.FROM_HTML_MODE_LEGACY)
            categoryLabel.text = post.category
            dateLabel.text = dateFormat.format(post.date)
            Glide.with(itemView).load(post.coverImage).into(imageView)

            itemView.setOnClickListener {
                onClick(post)
            }
        }
    }

    private var dataSet = mutableListOf<PostsFetcher.Post>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.post_item, viewGroup, false)

        view.findViewById<View>(R.id.image_and_date).apply {
            clipToOutline = true
            outlineProvider = RoundedOutlineProvider(5f)
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.bind(dataSet[position], listener)
    }

    override fun getItemCount() = dataSet.size

    fun append(posts: MutableList<PostsFetcher.Post>) {
        val previousItemCount = itemCount
        dataSet.addAll(posts)
        notifyItemRangeInserted(previousItemCount, posts.size)
    }

    fun replace(posts: MutableList<PostsFetcher.Post>) {
        val previousItemCount = itemCount
        dataSet = posts

        if (posts.size > previousItemCount) {
            notifyItemRangeInserted(previousItemCount, posts.size - previousItemCount)
            notifyItemRangeChanged(0, previousItemCount)
        } else {
            notifyItemRangeRemoved(posts.size, previousItemCount - posts.size)
            notifyItemRangeChanged(0, posts.size)
        }
    }
}