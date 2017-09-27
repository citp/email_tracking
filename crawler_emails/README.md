## Crawler for email clicks and views

This directory contains a fork of [OpenWPM](https://github.com/citp/OpenWPM)
that was customized to support viewing emails and viewing link clicks from
those emails. The crawler was also used to measure the number of successful
sign-ups as discussed in Section 3 of the paper. Full documentation is
available in the OpenWPM repository. Here we provide a summary of changes.

Crawl files:
* `crawl_tag.py` - Crawl to measure email views
* `crawl_click.py` - Crawl to measure link clicks from emails
* `crawl_signup.py` - Crawl to measure success rate for mailing list sign-up
* `crawl_tag_screenshot.py` - Crawl to measure email views for emails that were
    filtered using tracking protection lists. This crawl also takes screenshots
    of the rendered emails to allow inspection for visual corruption from
    resulting from filtering.

This repo contains a new config parameter, `spoof_mailclient`, which disables
Javascript and `Referer` headers in the browser. This is set for both tag
crawls, which simulate emails rendering in a webmail client.
