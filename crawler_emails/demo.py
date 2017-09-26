from automation import TaskManager, CommandSequence

NUM_BROWSERS = 1
sites = ['https://www.whatismybrowser.com/detect/is-javascript-enabled',
         'http://www.example.com',
         'http://www.princeton.edu',
         'http://citp.princeton.edu/'
         ]

manager_params, browser_params = TaskManager.load_default_params(NUM_BROWSERS)

for i in xrange(NUM_BROWSERS):
    browser_params[i]['http_instrument'] = True
    browser_params[i]['disable_flash'] = False
    browser_params[i]['spoof_mailclient'] = True

manager_params['data_directory'] = '~/Desktop/email_tracking_test/'
manager_params['log_directory'] = '~/Desktop/email_tracking_test/'

manager = TaskManager.TaskManager(manager_params, browser_params)

for site in sites:
    command_sequence = CommandSequence.CommandSequence(site)
    command_sequence.get(sleep=3, timeout=60)
    manager.execute_command_sequence(command_sequence)
manager.close()
