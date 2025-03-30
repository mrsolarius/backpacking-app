package fr.louisvolat.view

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import fr.louisvolat.R

/**
 * A simple [Fragment] subclass.
 * Use the [TravelsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TravelsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_travels, container, false)
    }
}