from automation import TaskManager, CommandSequence
from automation.Errors import CommandExecutionError
import crawl_utils as cu
import dill
import os

NUM_BROWSERS = 15
NUM_BATCH = 5000
LINKS_TO_VISIT = os.path.join('~/data/click_links.dill')

manager_params, browser_params = TaskManager.load_default_params(NUM_BROWSERS)

# Load site list
with open(LINKS_TO_VISIT, 'rb') as f:
    sites = dill.load(f)
TOTAL_NUM_SITES = len(sites)

# Configure browser
for i in xrange(NUM_BROWSERS):
    browser_params[i]['http_instrument'] = True
    browser_params[i]['cookie_instrument'] = True
    browser_params[i]['headless'] = True

prefix = '2017-05-17_email_tracking_click_crawl'
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
        command_sequence = CommandSequence.CommandSequence(sites[i][1],
                                                           sites[i][0],
                                                           reset=True)
        command_sequence.get(sleep=10, timeout=60)
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
cu.clear_tmp_folder()

# Remove index file if we are done
if current_index >= TOTAL_NUM_SITES:
    os.remove(os.path.expanduser('~/.openwpm/current_site_index'))
    with open(os.path.expanduser('~/.openwpm/crawl_done'), 'w') as f:
        f.write(str(1))
