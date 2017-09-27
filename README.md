## I never signed up for this! Privacy implications of email tracking

**Authors:** Steven Englehardt ([@englehardt](https://github.com/englehardt)),
Jeffrey Han ([@itdelatrisu](https://github.com/itdelatrisu)),
and Arvind Narayanan ([@randomwalker](https://github.com/randomwalker))

The paper is available [here](https://senglehardt.com/papers/pets2018_email_tracking.pdf).

This is a public code and data release for the research paper "I never signed
up for this! Privacy implications of email tracking.", which will appear at
PETS 2018. Portions of the code for this project borrow heavily from Jeffrey's
undergraduate senior thesis, available [here](https://github.com/itdelatrisu/thesis).


## Components
Core components:
* `crawler_emails/` - A web crawler to simulate email views and link clicks.
* `crawler_mailinglists/` - A web crawler to find and submit mailing list
    sign-ups.
* `email-tracking-tester/` - A tool to test the privacy properties of a mail
    client.
* `mailserver/` - The mail server used to collect our corpus of emails.
* `analysis/` - *Coming soon*

## Code Usage

### System Requirements
* The framework is fully tested only on Ubuntu 16.04, and requires Java and
  Python runtime environments.
* The processes (described below) can be run on separate machines. The mail
  server is OS-independent, but the web crawlers only run on Linux.
* Depending on the number of registered sites, the mail server might store
  anywhere from a few hundred megabytes to tens of gigabytes of data on disk
  per month.

### Processes
The system consists of three long-running processes:
* The mail server, which receives, stores, and analyzes incoming mail.
  ```
  $ cd mailsever
  $ mvn clean package
  $ java -jar target/mailserver.jar
  ```
* The mailing list crawler, which crawls a list of input sites and searches for
    mailing lists.
  ```
  $ cd crawler_mailinglists
  $ python crawl_mailinglist_signup.py
  ```
* The email crawler, which renders emails in a simulated webmail environment
    and visits links from those emails.
  ```
  $ cd crawler_emails
  $ python crawl_*.py
  ```

### SMTP Configuration
Running the mail server requires a domain name with MX records pointing to the
server. Additionally, if running the mailing list crawler from machines
other than the mail server's machine, host records (A, CNAME) must also be set.

## Data

*Coming soon*

## Funding

*TODO*
