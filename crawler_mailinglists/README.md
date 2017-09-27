## Crawler for mailing list sign-up and confirmation link verification

This directory contains a fork of [OpenWPM](https://github.com/citp/OpenWPM)
that was customized with a mailing list discovery command. Full documentation
is available in the OpenWPM repository. Here we provide a summary of changes.

The crawl file `crawl_mailinglist_signup.py` is used to sign-up for mailing
lists on the top sites given by the site lists in `data/`. To speed up
crawling, images are disabled with the `disable_images` config parameter.

This repo contains new commands to allow mailing list discovery and sign-up.
The code for doing so is located in the
[automation/Commands/custom_commands.py](https://github.com/citp/email_tracking/blob/master/crawler_mailinglists/automation/Commands/custom_commands.py)
file.
