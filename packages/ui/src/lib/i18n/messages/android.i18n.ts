/**
 * The strings the native Android app adds.
 *
 * They live here so the app and the web UI share one catalog. A key that a
 * translation has not caught up with falls back to the English catalog, the
 * same way every other key does.
 */
export const androidI18n = {
  'android.app.name': 'OpenChamber',
  'android.session.new': 'New session',
  'android.session.empty': 'No sessions yet',
  'android.session.untitled': 'Untitled session',
  'android.session.sessions': 'Sessions',
  'android.directory.title': 'Directory',
  'android.directory.projects': 'The projects folder',
  'android.projects.title': 'Projects',
  'android.projects.import': 'Import a folder',
  'android.projects.clone': 'Clone a repository',
  'android.projects.cloneUrl': 'Repository address',
  'android.projects.cloneName': 'Name on the phone',
  'android.projects.cloneAction': 'Clone',
  'android.projects.empty': 'No projects yet',
  'android.projects.cancel': 'Cancel',
  'android.composer.placeholder': 'Message',
  'android.composer.placeholderNoSession': 'Pick a session first',
  'android.composer.send': 'Send',
  'android.permission.allowOnce': 'Allow once',
  'android.permission.always': 'Always',
  'android.permission.reject': 'Reject',
  'android.form.send': 'Send',
  'android.form.field': 'Field',
  'android.runtime.stopped': 'OpenChamber is stopped',
  'android.runtime.serverReady': 'The server is up, the engine is starting',
  'android.runtime.engineStarting': 'The engine is starting',
  'android.runtime.ready': 'Ready',
  'android.runtime.retrying': 'OpenChamber keeps trying. The log in the settings says why it stopped.',
  'android.thinking': 'Thinking',
  'android.thinkingCollapsed': 'Thinking ({count} characters)',
  'android.tool.running': 'Running',
  'android.tool.preparing': 'Preparing the call',
  'android.settings.title': 'Settings',
  'android.settings.theme': 'Theme',
  'android.settings.theme.system': 'Follow the system',
  'android.settings.theme.light': 'Light',
  'android.settings.theme.dark': 'Dark',
  'android.settings.logs': 'Server log',
  'android.settings.about': 'About',
} as const;
