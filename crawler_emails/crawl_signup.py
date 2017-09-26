from automation import TaskManager, CommandSequence
import uuid
import re
import os

NUM_BROWSERS = 1
output_dir = os.path.expanduser('~/Desktop/')
db_name = 'crawl.sqlite'


def email_producer(url, site_title):
    """ Generate a random email address, matches the API of the producer used
    during data collection. See:
    https://github.com/itdelatrisu/OpenWPM/blob/906391b1903146496ad744d9e507d33bbbcadad8/crawl.py
    """
    return re.sub('-', '', str(uuid.uuid4())) + '@lorveskel.me'


manager_params, browser_params = TaskManager.load_default_params(NUM_BROWSERS)

for i in xrange(NUM_BROWSERS):
    browser_params[i]['headless'] = False
    browser_params[i]['bot_mitigation'] = True
    browser_params[i]['disable_flash'] = True
    browser_params[i]['disable_images'] = False
    browser_params[i]['http_instrument'] = True

manager_params['data_directory'] = output_dir
manager_params['log_directory'] = output_dir
manager_params['database_name'] = db_name

manager = TaskManager.TaskManager(manager_params, browser_params)

with open('data/sites_on_which_we_submitted_forms.txt', 'r') as f:
    sites = ['http://' + x for x in f.read().strip().split('\n')]

for site in sites:
    command_sequence = CommandSequence.CommandSequence(site)
    command_sequence.fill_forms(email_producer=email_producer, num_links=3,
                                timeout=150, page_timeout=8, debug=True)
    manager.execute_command_sequence(command_sequence)

manager.close()
