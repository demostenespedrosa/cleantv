# Listas de pacotes para a Semp TCL 55P725 (Google TV / Android 11)
# Tudo e desativado com "pm disable-user --user 0" (reversivel com restaurar.ps1).

# Camada 1 - seguro: propaganda, telemetria, apps nao usados, assistente de voz, perfil infantil
$Camada1 = @(
    # --- TCL: telemetria / propaganda / extras ---
    'com.tcl.bi'                       # telemetria (BI) - ~100 MB de RAM
    'com.tcl.usercenter'               # conta TCL
    'com.tcl.messagebox'               # mensagens/propaganda TCL
    'com.tcl.overseasappshow'          # vitrine de apps
    'com.tcl.waterfall.overseas'       # conteudo/recomendacoes TCL
    'com.tcl.channelplus'              # TCL Channel (canais online)
    'com.tcl.dashboard'                # painel TCL
    'com.tcl.guard'                    # "TGuard" limpador
    'com.tcl.smartalexa'               # Alexa
    'com.tcl.assistant'                # integracao do assistente
    'com.tcl.micmanager'               # gerenciador de microfone (voz)
    'com.tcl.MultiScreenInteraction_TV' # T-Cast / nScreen
    'com.tcl.notereminder'             # lembretes
    'com.tcl.esticker'                 # adesivo de loja / demonstracao
    'com.tcl.ocean.instructions'       # manual eletronico
    'com.tcl.hearaid'                  # aparelho auditivo
    'com.tcl.logkit'                   # coleta de logs
    'com.tcl.browser'                  # navegador TCL
    'com.tcl.gamebar'                  # barra de jogos
    'com.tcl.t_solo'                   # "ouvir sozinho"
    # --- Google ---
    'com.google.android.katniss'       # Assistente / busca por voz
    'com.google.android.apps.tv.dreamx' # protetor de tela Ambient (usa o basico do Android)
    'com.google.android.feedback'
    'com.google.android.play.games'
    'com.google.android.videos'        # Google TV / Play Filmes
    'com.google.android.youtube.tvkids'
    'com.google.android.apps.tachyon'  # Duo / Meet
    'com.google.android.marvin.talkback'
    'com.google.android.syncadapters.calendar'
    'android.autoinstalls.config.google.gtvpai' # instala apps de parceiros sozinho
    # --- AOSP inuteis em TV ---
    'com.android.printspooler'
    'com.android.camera2'
    'com.android.messaging'
    'com.android.dynsystem'
    'org.chromium.webview_shell'
)

# Camada 2 - risco medio (pode desativar se quiser economizar mais)
$Camada2 = @(
    'com.android.settings.intelligence' # busca/sugestoes nas configuracoes (~75 MB)
    'com.tcl.miracast'                  # espelhamento Miracast (Chromecast continua funcionando)
    'com.tcl.gallery'                   # galeria TCL (Media Center continua)
)

# Camada 3 - troca de launcher (so depois do TV Home instalado!)
$Camada3 = @(
    'com.google.android.apps.tv.launcherx'     # launcher Google TV (~180 MB)
    'com.google.android.tungsten.setupwraith'  # assistente de setup (tambem registra uma tela "Home")
)

# NUNCA desativar (essenciais): Play Store/Services, com.tcl.tv, com.tcl.tvinput, com.tcl.settings,
# com.tcl.suspension, com.tcl.tcl_bt_rcu_service, com.tcl.autopair, com.tcl.keyhelp, com.tcl.partnercustomizer,
# com.tcl.system.server, com.tcl.providers.config, com.tcl.xian.StartandroidService, com.tcl.versionUpdateApp,
# com.tcl.inputmethod.international (teclado padrao), com.android.tv.settings, mediashell (Chromecast).
