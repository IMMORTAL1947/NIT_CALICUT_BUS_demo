package com.example.nit_calciut_bus_demo.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.nit_calciut_bus_demo.R

class CollegeConfigFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_college_config, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val codeField = view.findViewById<EditText>(R.id.inputCollegeCode)
        val serverField = view.findViewById<EditText>(R.id.inputServerUrl)
        val saveBtn = view.findViewById<Button>(R.id.btnSaveCollege)
    val testBtn = view.findViewById<Button>(R.id.btnTestConnection)

        codeField.setText(AppPrefs.getCollegeCode(requireContext()) ?: "")
        serverField.setText(AppPrefs.getServerUrl(requireContext()))

        saveBtn.setOnClickListener {
            val code = codeField.text.toString().trim()
            val url = serverField.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(requireContext(), "Enter college code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Enter server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setCollegeCode(requireContext(), code)
            AppPrefs.setServerUrl(requireContext(), url)
            Toast.makeText(requireContext(), "Saved. Restart Bus Stops/Routes to reload.", Toast.LENGTH_LONG).show()
        }

        testBtn.setOnClickListener {
            val code = codeField.text.toString().trim()
            val url = serverField.text.toString().trim().removeSuffix("/")
            if (code.isEmpty() || url.isEmpty()) {
                Toast.makeText(requireContext(), "Enter code and server URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    val testUrl = java.net.URL("${url}/api/colleges/${code}/config")
                    val conn = testUrl.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    val status = conn.responseCode
                    if (status == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(body)
                        val stops = json.optJSONArray("stops")?.length() ?: 0
                        val routes = json.optJSONArray("routes")?.length() ?: 0
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Success: ${stops} stops, ${routes} routes", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "HTTP ${status} - check server/college code", Toast.LENGTH_LONG).show()
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }
}
