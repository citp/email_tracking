from automation import TaskManager, CommandSequence
from automation.Errors import CommandExecutionError
import crawl_utils as cu
import os

NUM_BROWSERS = 15
NUM_BATCH = 5000
MAIL_DIR = os.path.expanduser('~/data/html/')  # Directory of HTML files
PORT = 8000
EMAIL_DOMAIN = ''

manager_params, browser_params = TaskManager.load_default_params(NUM_BROWSERS)

# Set up server
server = cu.HTTPServer(MAIL_DIR, PORT)
server.start()

# Load site list
sites = cu.grab_mail_urls(MAIL_DIR, PORT, EMAIL_DOMAIN)
TOTAL_NUM_SITES = len(sites)

# Configure browser
for i in xrange(NUM_BROWSERS):
    browser_params[i]['http_instrument'] = True
    browser_params[i]['cookie_instrument'] = True
    browser_params[i]['spoof_mailclient'] = True
    browser_params[i]['headless'] = True

prefix = '2017-05-28_email_tracking_filtered_pages'
manager_params['database_name'] = prefix + '.sqlite'
manager_params['data_directory'] = '~/Desktop/email_tracking/'
manager_params['log_directory'] = '~/Desktop/email_tracking/'

# Manage control files
if not os.path.isdir(os.path.expanduser('~/.openwpm/')):
    os.mkdir(os.path.expanduser('~/.openwpm/'))
if os.path.isfile(os.path.expanduser('~/.openwpm/reboot')):
    os.remove(os.path.expanduser('~/.openwpm/reboot'))
if os.path.isfile(os.path.expanduser('~/.openwpm/current_site_index')):
    with open(os.path.expanduser('~/.openwpm/current_site_index'), 'r') as f:
        start_index = int(f.read()) + 1
    end_index = start_index + NUM_BATCH
else:
    start_index = 0
    end_index = NUM_BATCH + 1

# Start crawling
manager = TaskManager.TaskManager(manager_params, browser_params)
current_index = 0
for i in range(start_index, end_index):
    current_index = i
    if current_index >= TOTAL_NUM_SITES:
        break
    try:
        command_sequence = CommandSequence.CommandSequence(sites[i],
                                                           reset=True)
        command_sequence.get(sleep=10, timeout=60)
        shot_name = '_'.join(sites[i][:-5].split('/')[-2:])
        command_sequence.save_screenshot(shot_name)
        manager.execute_command_sequence(command_sequence)
        with open(os.path.expanduser('~/.openwpm/current_site_index'),
                  'w') as f:
            f.write(str(i))
    except CommandExecutionError:
        with open(os.path.expanduser('~/.openwpm/stop'), 'w') as f:
            f.write(str(1))
        break

# Shut down and clean up after batch
manager.close()
server.stop()
cu.clear_tmp_folder()

# Remove index file if we are done
if current_index >= TOTAL_NUM_SITES:
    os.remove(os.path.expanduser('~/.openwpm/current_site_index'))
    with open(os.path.expanduser('~/.openwpm/crawl_done'), 'w') as f:
        f.write(str(1))
