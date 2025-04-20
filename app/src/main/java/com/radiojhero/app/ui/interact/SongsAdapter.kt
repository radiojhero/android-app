package com.radiojhero.app.ui.interact

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.bumptech.glide.Glide
import com.radiojhero.app.R
import com.radiojhero.app.RoundedOutlineProvider
import com.radiojhero.app.fetchers.SongsFetcher.Song
import com.radiojhero.app.normalize

class SongsAdapter(
    context: Context, @LayoutRes private val layoutResource: Int, private val allSongs: List<Song>
) : ArrayAdapter<Song>(context, layoutResource, allSongs), Filterable {
    private var mSongs: List<Song> = allSongs

    override fun getCount(): Int {
        return mSongs.size
    }

    override fun getItem(p0: Int): Song {
        return mSongs[p0]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view =
            convertView ?: LayoutInflater.from(context).inflate(layoutResource, parent, false)
        val song = mSongs[position]
        view.findViewById<TextView>(R.id.album_label).apply {
            text = song.album
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.artist_label).apply {
            text = song.artist
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.title_label).apply {
            text = song.title
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.image_wrapper).apply {
            clipToOutline = true
            outlineProvider = RoundedOutlineProvider(5f)
        }
        Glide.with(view).load(song.cover).into(view.findViewById(R.id.image_view))

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
                mSongs = filterResults.values as List<Song>
                notifyDataSetChanged()
            }

            override fun performFiltering(charSequence: CharSequence?): FilterResults {
                val queryString = charSequence?.toString()?.normalize()

                val filterResults = FilterResults()
                filterResults.values = if (queryString.isNullOrEmpty()) allSongs
                else allSongs.filter {
                    it.album.normalize().contains(queryString) || it.artist.normalize()
                        .contains(queryString) || it.title.normalize().contains(queryString)
                }
                return filterResults
            }
        }
    }
}