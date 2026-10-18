#!/usr/bin/env python3
"""Read-only production API smoke check, run from CI (no client/server secrets)."""
import json
import os
import urllib.parse
import urllib.request

BASE = 'https://store.gothwadtech.com/api/'

def read(path):
    req = urllib.request.Request(BASE + path, headers={'Accept': 'application/json', 'User-Agent': 'KiwiStore-CI/1.0'})
    with urllib.request.urlopen(req, timeout=30) as response:
        if 'application/json' not in response.headers.get('Content-Type', ''):
            raise ValueError('Store route returned HTML/non-JSON instead of Worker JSON: ' + path)
        data = json.load(response)
    if not isinstance(data, dict) or data.get('source') == 'unconfigured' or data.get('error'):
        raise ValueError('Store Worker is unavailable or unconfigured: ' + path)
    return data


def main():
    catalog = read('store/apps')
    categories = read('store/categories')
    if not isinstance(catalog.get('apps'), list) or not isinstance(categories.get('categories'), list):
        raise ValueError('Catalog/categories do not match the Android wire contract')
    apps = catalog['apps']
    for app in apps:
        if not all(isinstance(app.get(k), str) and app[k] for k in
                   ('id', 'packageName', 'name', 'developerName', 'category', 'kind', 'status')):
            raise ValueError('A catalog listing is missing required Android identity fields')
        if app['status'] != 'published' or app['kind'] not in ('app', 'game'):
            raise ValueError('Unexpected unpublished listing/app kind in public catalog')
    if apps:
        detail = read('store/apps/' + urllib.parse.quote(apps[0]['id'], safe=''))
        if detail.get('app', {}).get('id') != apps[0]['id'] or not isinstance(detail.get('reviews'), list):
            raise ValueError('App detail does not match the Android contract')
        for release in detail.get('releases', []):
            if release.get('isLive') and release.get('reviewStatus') != 'approved':
                raise ValueError('Public app detail contains an unapproved live release')
    summary = (f'Production Worker smoke check passed: {len(apps)} published apps, '
               f'{len(categories["categories"])} categories. '
               'Catalog/category JSON and one detail response checked; no downloads or account mutations made.')
    print(summary)
    if not apps:
        print('::warning::Catalog is empty; populated listing and APK behavior still need device testing.')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as f:
            f.write('\n## Store API smoke check\n' + summary + '\n')

if __name__ == '__main__':
    main()
