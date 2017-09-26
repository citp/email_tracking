from automation import TaskManager, CommandSequence
from urllib import urlencode
from urllib2 import Request, urlopen

# Constants
NUM_BROWSERS = 1
output_dir = 'output_crawl/'
db_name = 'crawl.sqlite'
# Site list one of: shopping-500.csv, news-500.csv, top-1m.csv
site_list = 'data/shopping-500.csv'


def get_site(line):
    return 'http://' + line.strip().split(',')[1] if line.count(',') >= 1 else None


# Email address producer function (called when filling a form)
def get_email(url, site_title):
    # Registers an email address with the mail API, returns the email address
    api = 'http://lorveskel.me:8080/register'
    data = urlencode({'site': site_title, 'url': url})
    req = Request(api, data)
    response = urlopen(req)
    return response.read()


# Visits the sites
def crawl_site(site, manager, email_producer):
    command_sequence = CommandSequence.CommandSequence(site)
    command_sequence.fill_forms(email_producer=email_producer, num_links=3,
                                timeout=120, page_timeout=8, debug=False)
    manager.execute_command_sequence(command_sequence)


# Loads the manager preference and the default browser dictionaries
manager_params, browser_params = TaskManager.load_default_params(NUM_BROWSERS)

# Update browser configuration (use this for per-browser settings)
for i in xrange(NUM_BROWSERS):
    browser_params[i]['headless'] = True
    browser_params[i]['bot_mitigation'] = True
    browser_params[i]['disable_flash'] = True
    browser_params[i]['disable_images'] = True
    browser_params[i]['http_instrument'] = False

# Update TaskManager configuration (use this for crawl-wide settings)
manager_params['data_directory'] = output_dir
manager_params['log_directory'] = output_dir
manager_params['database_name'] = db_name

# Instantiates the measurement platform
manager = TaskManager.TaskManager(manager_params, browser_params)

# Read site list
index = 0
start_site_index = 0
with open(site_list) as f:
    for line in f:
        index += 1
        if index < start_site_index:
            continue
        site = get_site(line)
        if site is not None:
            crawl_site(site, manager, get_email)

# Shuts down the browsers and waits for the data to finish logging
manager.close()
