package com.example.nit_calciut_bus_demo.ui.theme

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nit_calciut_bus_demo.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class RequestFormFragment : Fragment() {
    private lateinit var vm: RequestViewModel
    private var selectedDateTime: Calendar = Calendar.getInstance()
    private var selectedPickupLocation: String = "Not selected"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_request_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(this)[RequestViewModel::class.java]

        val dest = view.findViewById<EditText>(R.id.destinationInput)
        val purpose = view.findViewById<EditText>(R.id.purposeInput)
        val passengerCount = view.findViewById<TextView>(R.id.passengerCount)
        val dec = view.findViewById<Button>(R.id.decPassenger)
        val inc = view.findViewById<Button>(R.id.incPassenger)
        val datetimeBtn = view.findViewById<Button>(R.id.datetimeButton)
        val pickMapBtn = view.findViewById<Button>(R.id.pickMapButton)
        val submit = view.findViewById<Button>(R.id.submitRequest)

        var passengers = 1
        passengerCount.text = passengers.toString()
        dec.setOnClickListener { if (passengers > 1) { passengers--; passengerCount.text = passengers.toString() } }
        inc.setOnClickListener { passengers++; passengerCount.text = passengers.toString() }

        datetimeBtn.setOnClickListener { pickDateTime(datetimeBtn) }
        
        pickMapBtn.setOnClickListener { 
            // Navigate to map picker
            val mapFragment = MapPickerFragment.newInstance { location ->
                selectedPickupLocation = location
                pickMapBtn.text = "📍 ${location.take(30)}"
            }
            parentFragmentManager.beginTransaction()
                .replace((view.parent as ViewGroup).id, mapFragment)
                .addToBackStack(null)
                .commit()
        }

        submit.setOnClickListener {
            if (selectedPickupLocation == "Not selected") {
                Toast.makeText(requireContext(), "Please select a pickup location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val payload = JSONObject()
            payload.put("route", "LH → City Center")
            payload.put("destination", dest.text.toString())
            payload.put("dateTime", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(selectedDateTime.time))
            payload.put("passengers", passengers)
            payload.put("purpose", purpose.text.toString())
            payload.put("roundTrip", view.findViewById<android.widget.Switch>(R.id.returnToggle).isChecked)

            vm.submitRequest(requireContext(), payload) { ok ->
                requireActivity().runOnUiThread {
                    if (ok) {
                        Toast.makeText(requireContext(), "Request submitted", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else Toast.makeText(requireContext(), "Failed to submit", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pickDateTime(button: Button) {
        val now = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            selectedDateTime.set(Calendar.YEAR, y)
            selectedDateTime.set(Calendar.MONTH, m)
            selectedDateTime.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(requireContext(), { _, h, min ->
                selectedDateTime.set(Calendar.HOUR_OF_DAY, h)
                selectedDateTime.set(Calendar.MINUTE, min)
                button.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(selectedDateTime.time)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }
}
