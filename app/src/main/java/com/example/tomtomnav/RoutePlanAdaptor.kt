package com.example.tomtomnav

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tomtom.sdk.routing.route.Route

class RoutePlanAdaptor(
    private val dataSet: ArrayList<Route>,
    private val onClick: (Route) -> Unit
) :
    RecyclerView.Adapter<RoutePlanAdaptor.ViewHolder>() {

    class ViewHolder(view: View, val onClick: (Route) -> Unit) : RecyclerView.ViewHolder(view) {
        private var currentRoute: Route? = null
        private val travelTimeText: TextView = view.findViewById(R.id.travel_time)
        private val travelLengthText: TextView = view.findViewById(R.id.travel_length)

        init {
            view.setOnClickListener {
                currentRoute?.let {
                    onClick(it)
                }
            }
        }

        fun bind(route: Route) {
            currentRoute = route

            travelTimeText.text = route.summary.travelTime.toString()
            travelLengthText.text = route.summary.length.inWholeKilometers().toString() + " km"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.route_plan_item, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun getItemCount() = dataSet.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = dataSet[position]
        holder.bind(route)
    }

    fun addRoute(route: Route) {
        dataSet.add(route)
        this.notifyItemInserted(dataSet.size - 1)
    }

    fun clear() {
        dataSet.clear()
        this.notifyDataSetChanged()
    }
}