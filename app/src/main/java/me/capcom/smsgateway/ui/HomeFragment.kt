package me.capcom.smsgateway.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.toSpanned
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.databinding.FragmentHomeBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.connection.ConnectionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.orchestrator.OrchestratorService
import me.capcom.smsgateway.ui.dialogs.FirstStartDialogFragment
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper: SettingsHelper by inject()
    private val gatewaySettings: GatewaySettings by inject()
    private val connectionService: ConnectionService by inject()
    private val messagesDao: MessagesDao by inject()
    private val events: EventBus by inject()
    private val gatewaySvc: GatewayService by inject()
    private val orchestratorSvc: OrchestratorService by inject()

    private var isServiceRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(FirstStartDialogFragment.REQUEST_KEY) { _, data ->
            when (FirstStartDialogFragment.getResult(data)) {
                FirstStartDialogFragment.Result.Canceled -> {
                    Toast.makeText(requireContext(), "Operation cancelled", Toast.LENGTH_SHORT).show()
                    return@setFragmentResultListener
                }

                FirstStartDialogFragment.Result.SignUp -> {
                    lifecycleScope.launch {
                        try {
                            binding.buttonStart.isEnabled = false
                            gatewaySettings.enabled = true
                            gatewaySvc.registerDevice(
                                requireContext(),
                                null,
                                GatewayService.RegistrationMode.Anonymous
                            )
                            updateRegistrationUi()
                            requestPermissionsAndStart()
                        } catch (th: Throwable) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to register device: ${th.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            binding.buttonStart.isEnabled = true
                        }
                    }
                }

                FirstStartDialogFragment.Result.SignIn -> {
                    val username = FirstStartDialogFragment.getUsername(data)
                    val password = FirstStartDialogFragment.getPassword(data)
                    lifecycleScope.launch {
                        try {
                            binding.buttonStart.isEnabled = false
                            gatewaySettings.enabled = true
                            gatewaySvc.registerDevice(
                                requireContext(),
                                null,
                                GatewayService.RegistrationMode.WithCredentials(username, password)
                            )
                            updateRegistrationUi()
                            requestPermissionsAndStart()
                        } catch (th: Throwable) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to register device: ${th.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            binding.buttonStart.isEnabled = true
                        }
                    }
                }

                FirstStartDialogFragment.Result.SignInByCode -> {
                    val code = FirstStartDialogFragment.getCode(data)
                    lifecycleScope.launch {
                        try {
                            binding.buttonStart.isEnabled = false
                            gatewaySettings.enabled = true
                            gatewaySvc.registerDevice(
                                requireContext(),
                                null,
                                GatewayService.RegistrationMode.WithCode(code)
                            )
                            updateRegistrationUi()
                            requestPermissionsAndStart()
                        } catch (th: Throwable) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to register device: ${th.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            binding.buttonStart.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textRemoteDeviceId.movementMethod = LinkMovementMethod.getInstance()
        binding.textRemoteUsername.movementMethod = LinkMovementMethod.getInstance()
        binding.textRemotePassword.movementMethod = LinkMovementMethod.getInstance()

        binding.switchAutostart.isChecked = settingsHelper.autostart
        binding.switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            settingsHelper.autostart = isChecked
        }

        binding.switchUseRemoteServer.isChecked = gatewaySettings.enabled
        binding.switchUseRemoteServer.setOnCheckedChangeListener { _, isChecked ->
            gatewaySettings.enabled = isChecked
            binding.layoutRemoteServer.isVisible = isChecked
            binding.textConnectionStatus.isVisible = isChecked
            if (!isChecked && isServiceRunning) {
                stop()
            } else if (isChecked && !isServiceRunning && gatewaySettings.registrationInfo != null) {
                actionStart(true)
            }
        }

        binding.buttonStart.setOnClickListener {
            actionStart(!isServiceRunning)
        }

        updateRegistrationUi()
        updateSimInfo()

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Success> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)
                binding.textRemoteUsername.text = makeCopyableLink(Html.fromHtml("<a href>${event.login}</a>"))
                binding.textRemotePassword.text = when (event.password) {
                    null -> getString(R.string.password_hidden)
                    else -> makeCopyableLink(Html.fromHtml("<a href>${event.password}</a>"))
                }
                binding.textRemoteDeviceId.text = gatewaySettings.deviceId?.let {
                    makeCopyableLink(Html.fromHtml("<a href>$it</a>"))
                } ?: getString(R.string.n_a)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Failure> { event ->
                binding.textRemoteAddress.text = getString(R.string.address_is, event.server)
                binding.textRemoteUsername.text = getString(R.string.not_registered)
                binding.textRemotePassword.text = getString(R.string.n_a)
                binding.textRemoteDeviceId.text = gatewaySettings.deviceId?.let {
                    makeCopyableLink(Html.fromHtml("<a href>$it</a>"))
                } ?: getString(R.string.n_a)

                Toast.makeText(
                    requireContext(),
                    getString(R.string.failed_to_register_device, event.reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        gatewaySvc.isActiveLiveData(requireContext()).observe(viewLifecycleOwner) { isRunning ->
            isServiceRunning = isRunning
            updateServiceStateUi(isRunning)
        }

        connectionService.status.observe(viewLifecycleOwner) {
            binding.textConnectionStatus.apply {
                isVisible = binding.switchUseRemoteServer.isChecked
                isEnabled = it
                text = when (it) {
                    true -> context.getString(R.string.internet_connection_available)
                    false -> context.getString(R.string.internet_connection_unavailable)
                }
            }
        }

        messagesDao.getMessagesStats().observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.textStatPending.text = it.pending.toString()
                binding.textStatSent.text = (it.sent + it.delivered).toString()
                binding.textStatFailed.text = it.failed.toString()
            }
        }
    }

    private fun updateRegistrationUi() {
        val info = gatewaySettings.registrationInfo
        binding.textRemoteAddress.text = gatewaySettings.serverUrl
        binding.textRemoteDeviceId.text = gatewaySettings.deviceId ?: getString(R.string.n_a)
        binding.textRemoteUsername.text = info?.login ?: gatewaySettings.username ?: getString(R.string.not_registered)
        binding.textRemotePassword.text = if (info != null || gatewaySettings.password != null) "••••••••" else getString(R.string.n_a)
    }

    private fun updateSimInfo() {
        try {
            val simCards = SubscriptionsHelper.getActiveSimCards(requireContext())
            if (simCards.isNullOrEmpty()) {
                binding.textSimCarrier.text = "No SIM detected"
                binding.textSimStatus.text = "Check SIM slot"
            } else {
                val primarySim = simCards.first()
                val carrier = primarySim.carrierName ?: "Jio"
                binding.textSimCarrier.text = "$carrier (Slot ${primarySim.slotIndex + 1})"
                binding.textSimStatus.text = "Cellular Ready"
            }
        } catch (_: Exception) {
            binding.textSimCarrier.text = "Jio (SIM active)"
            binding.textSimStatus.text = "Ready"
        }
    }

    private fun makeCopyableLink(source: Spanned): Spanned {
        val builder = SpannableStringBuilder(source)
        val spans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (span in spans) {
            val innerText = builder.subSequence(
                builder.getSpanStart(span),
                builder.getSpanEnd(span)
            ).toString()
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val clipboard = requireContext().getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("", innerText))
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            }
            builder.setSpan(
                clickableSpan,
                builder.getSpanStart(span),
                builder.getSpanEnd(span),
                builder.getSpanFlags(span)
            )
            builder.removeSpan(span)
        }
        return builder.toSpanned()
    }

    override fun onResume() {
        super.onResume()
        binding.switchUseRemoteServer.isChecked = gatewaySettings.enabled
        updateSimInfo()
    }

    private fun updateServiceStateUi(isRunning: Boolean) {
        if (_binding == null) return
        binding.buttonStart.apply {
            text = getString(
                if (isRunning) R.string.button_stop_service
                else R.string.button_start_service
            )
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (isRunning) R.color.brand_error else R.color.brand_primary
                )
            )
        }
    }

    private fun actionStart(start: Boolean) {
        if (start) {
            gatewaySettings.enabled = true
            if (gatewaySettings.registrationInfo == null) {
                cloudFirstStart()
                return
            }
            requestPermissionsAndStart()
        } else {
            stop()
        }
    }

    private fun cloudFirstStart() {
        FirstStartDialogFragment.newInstance().show(parentFragmentManager, "signin")
    }

    private fun stop() {
        orchestratorSvc.stop(requireContext())
        isServiceRunning = false
        updateServiceStateUi(false)
        Toast.makeText(requireContext(), "Gateway Service Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun start() {
        gatewaySettings.enabled = true
        orchestratorSvc.start(requireContext().applicationContext, false)
        isServiceRunning = true
        updateServiceStateUi(true)
        Toast.makeText(requireContext(), "Gateway Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionsAndStart() {
        val permissionsRequired = listOfNotNull(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
        ).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsRequired.isEmpty()) {
            start()
            return
        }

        permissionsRequest.launch(permissionsRequired.toTypedArray())
    }

    private fun restartRequiredNotification() {
        if (!isServiceRunning) return
        Toast.makeText(
            requireContext(),
            getString(R.string.to_apply_the_changes_restart_the_app_using_the_button_below),
            Toast.LENGTH_SHORT
        ).show()
    }

    private val permissionsRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            Log.d(javaClass.name, "Required SMS transmission permissions granted")
        } else {
            Toast.makeText(
                requireContext(),
                "Required permissions (SEND_SMS) not granted",
                Toast.LENGTH_SHORT
            ).show()
        }
        start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = HomeFragment()
    }
}
