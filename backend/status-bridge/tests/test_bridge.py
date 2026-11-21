import base64
import copy
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta
from http.server import ThreadingHTTPServer
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from model import transfer, classify, matching_torrent, identity_match, series_summary
from bridge import Bridge, Denied
from adapters import UpstreamError, Transport, credential
from server import handler, private_bind

NOW = datetime(2026, 11, 21, 12, 0, tzinfo=timezone.utc)
HASH = 'a' * 40
AUTH = 'Seerr ' + base64.b64encode(b'connect.sid=synthetic').decode()
USER_ID = 'b' * 32

class Fake:
    def __init__(self):
        self.calls = []
        self.fail = set()
        self.movie = {'id': 10, 'tmdbId': 101, 'monitored': True, 'hasFile': False}
        self.queue = [{'movieId': 10, 'downloadId': HASH, 'title': 'PRIVATE', 'outputPath': '/private'}]
        self.qb = [{'hash': HASH, 'name': 'PRIVATE', 'tracker': 'SECRET', 'save_path': '/private', 'state': 'downloading', 'progress': .74, 'dlspeed': 32_400_000, 'amount_left': 1000, 'eta': 300}]
        self.tv_queue = [{"seriesId": 20, "episodeId": 7, "downloadId": HASH}]
        self.episodes = [{'id': n, 'seasonNumber': 2, 'episodeNumber': n, 'hasFile': n < 7, 'episodeFileId': n, 'monitored': True} for n in range(1, 9)]
        self.local = [{'ParentIndexNumber': 2, 'IndexNumber': n, 'Type': 'Episode', 'MediaSources': [{'Id': 'file'}]} for n in range(1, 7)]
        self.owned = {'id': 1, 'requestedBy': {'id': 7}, 'status': 2, 'media': {'mediaType': 'movie', 'tmdbId': 101, 'status': 3}}
        self.series = False
        self.jf_movie = False
    def get(self, service, path, headers=None, cache=True):
        self.calls.append((service, path))
        if service in self.fail: raise UpstreamError(service)
        if service == 'seerr':
            if path == 'api/v1/auth/me': return {'id': 7, 'jellyfinUserId': USER_ID, 'permissions': 2}
            if path.startswith('api/v1/request'):
                r = copy.deepcopy(self.owned)
                if path == 'api/v1/request/2': r['id'] = 2; r['requestedBy']['id'] = 8
                if self.series:
                    r['media'] = {'mediaType': 'tv', 'tmdbId': 201, 'tvdbId': 301, 'status': 4}
                    r['seasons'] = [{'seasonNumber': 2}]
                return {'results': [r]} if '?' in path else r
        if service == 'radarr':
            if '/movie?' in path: return [self.movie]
            if '/queue?' in path: return {'records': self.queue}
            return []
        if service == 'sonarr':
            if '/series?' in path: return [{'id': 20, 'tvdbId': 301, 'monitored': True}]
            if '/queue?' in path: return {'records': self.tv_queue}
            if '/episode?' in path: return self.episodes
            if '/episodefile?' in path: return [{'id': n} for n in range(1, 7)]
            return []
        if service == 'jellyfin':
            if path == 'Users/Me': return {'Id': USER_ID}
            if '/Items?' in path:
                return {'Items': [{'Id': 'c' * 32, 'Type': 'Series' if self.series else 'Movie', 'ProviderIds': {'tvdb': '301'} if self.series else {'tmdb': '101'}, 'MediaSources': [{'Id': 'file'}]}] if self.series or self.jf_movie else []}
            if '/Episodes?' in path: return {'Items': self.local}
        raise AssertionError((service, path))
    def torrents(self, ids):
        self.calls.append(('qbit', 'filtered'))
        if 'qbit' in self.fail: raise UpstreamError('qbit')
        return self.qb if HASH in ids else []

class Rules(unittest.TestCase):
    def test_downloading_and_two_distinct_etas(self):
        result = transfer(Fake().qb[0], NOW, NOW)
        self.assertEqual(result['progress'], .74)
        self.assertEqual(result['downloadEta']['estimatedAt'], '2026-11-21T12:05:00Z')
        self.assertIsNone(result['availabilityEta']['estimatedAt'])
    def test_paused_stalled_error_and_queued_states(self):
        for state, expected in [('stoppedDL','PAUSED'),('pausedDL','PAUSED'),('stalledDL','STALLED'),('error','FAILED'),('missingFiles','FAILED'),('queuedDL','QUEUED')]:
            torrent = dict(Fake().qb[0], state=state)
            result = transfer(torrent, NOW, NOW)
            self.assertEqual(result['state'], expected)
            self.assertIsNone(result['downloadEta']['estimatedAt'])
    def test_invalid_and_unbounded_eta_speed_remaining_and_stale(self):
        for patch in [{'eta': -1}, {'eta': 8640000}, {'eta': 0}, {'eta': float('nan')}, {'dlspeed': 0}, {'amount_left': 0}, {'progress': 1}, {'progress': float('inf')}]:
            result = transfer(dict(Fake().qb[0], **patch), NOW, NOW)
            self.assertIsNone(result['downloadEta']['estimatedAt'])
        self.assertTrue(transfer(Fake().qb[0], NOW - timedelta(seconds=60), NOW)['stale'])
        self.assertEqual(transfer(Fake().qb[0], NOW - timedelta(seconds=60), NOW)['state'], 'UNKNOWN')
    def test_stable_ids_not_names_and_ambiguity(self):
        self.assertIsNone(matching_torrent('same-name', [{'name': 'same-name'}]))
        self.assertIsNotNone(matching_torrent(HASH.upper(), Fake().qb))
        self.assertIsNone(matching_torrent(HASH, Fake().qb * 2))
        self.assertIsNone(identity_match([{'tmdbId': 1, 'title': 'Same'}], 'tmdbId', 2))
        self.assertIsNone(identity_match([{'tvdbId': 2}] * 2, 'tvdbId', 2))
    def test_import_and_jellyfin_gates(self):
        req = {'status': 2}; item = {'monitored': True}
        self.assertEqual(classify(req,item,[],[],NOW,NOW,imported=True)['state'], 'WAITING_FOR_JELLYFIN')
        self.assertEqual(classify(req,item,[],[],NOW,NOW,jellyfin=True)['state'], 'AVAILABLE')
        done = dict(Fake().qb[0], state='uploading', progress=1)
        self.assertEqual(classify(req,item,[{'downloadId':HASH}],[done],NOW,NOW)['state'],'DOWNLOAD_COMPLETE')
        self.assertEqual(classify(req,item,[{'trackedDownloadState':'importing'}],[],NOW,NOW)['state'],'IMPORTING')
        self.assertEqual(classify(req,item,[{'trackedDownloadState':'importFailed'}],[],NOW,NOW)['state'],'FAILED')
    def test_searching_requires_explicit_evidence(self):
        self.assertEqual(classify({'status':2},{'monitored':True},[],[],NOW,NOW)['state'],'APPROVED')
        self.assertEqual(classify({'status':2},{'monitored':True},[],[],NOW,NOW,searching=True)['state'],'SEARCHING')
        self.assertEqual(classify({'status':2},{'monitored':False},[],[],NOW,NOW)['state'],'NOT_MONITORED')
    def test_multiple_downloads_never_averaged(self):
        a, b = Fake().qb[0], dict(Fake().qb[0], hash='b'*40,progress=.2)
        result = classify({'status':2},{},[{'downloadId':HASH},{'downloadId':'b'*40}],[a,b],NOW,NOW)
        self.assertIsNone(result['progress']); self.assertEqual(len(result['transfers']),2)

class Aggregation(unittest.TestCase):
    def setUp(self): self.fake = Fake(); self.bridge = Bridge(self.fake, lambda: NOW)
    def status(self, token='synthetic'):
        user, cookie = self.bridge.authenticate(AUTH)
        return self.bridge.statuses(user,cookie,token,[1])['requests'][0]
    def test_seerr_radarr_qbit_correlate_and_strip_private_fields(self):
        value = self.status()
        self.assertEqual(value['requestId'],1);self.assertEqual(value['status']['state'],'DOWNLOADING')
        raw = json.dumps(value)
        for forbidden in [HASH,'PRIVATE','SECRET','/private','downloadId','tracker','save_path']:
            self.assertNotIn(forbidden,raw)
    def test_other_users_request_not_authorized_even_for_admin(self):
        user, cookie = self.bridge.authenticate(AUTH)
        with self.assertRaises(Denied) as failure:self.bridge.statuses(user,cookie,'synthetic',[2])
        self.assertEqual(failure.exception.status,404)
        self.assertFalse(any(service in {'radarr','sonarr','qbit'} for service,_ in self.fake.calls))
    def test_missing_or_invalid_auth_fails_before_data(self):
        for auth in ['', 'Bearer secret', 'Seerr invalid', 'Seerr ' + base64.b64encode(b'connect.sid=secret\r\nX: bad').decode()]:
            with self.assertRaises(Denied):self.bridge.authenticate(auth)
        self.assertEqual(self.fake.calls,[])
    def test_partial_sonarr_season_and_individual_progress(self):
        self.fake.series=True
        value=self.status()
        self.assertEqual(value['seasons'][0]['availableEpisodes'],6)
        self.assertEqual(value['seasons'][0]['totalEpisodes'],8)
        self.assertEqual(value['status']['state'],'PARTIALLY_AVAILABLE')
        self.assertIsNone(value['status']['progress'])
        self.assertEqual(value['episodes'][6]['status']['progress'],.74)
        self.assertIsNone(value['episodes'][7]['status']['progress'])
    def test_pack_percentage_is_not_presented_as_individual_episode_progress(self):
        self.fake.series=True
        self.fake.tv_queue.append({'seriesId':20,'episodeId':8,'downloadId':HASH})
        value=self.status()
        self.assertIsNone(value['episodes'][6]['status']['progress'])
        self.assertIsNone(value['episodes'][7]['status']['progress'])
        self.assertEqual(len(value['status']['transfers']),1)
        self.assertEqual(value['status']['transfers'][0]['progress'],.74)
    def test_future_episode_waiting_for_release(self):
        self.fake.series=True;self.fake.episodes[7]['airDateUtc']='2026-11-22T00:00:00Z'
        self.assertEqual(self.status()['episodes'][7]['status']['state'],'WAITING_FOR_RELEASE')
    def test_arr_import_does_not_prove_jellyfin_availability(self):
        self.fake.movie['hasFile']=True
        self.assertEqual(self.status()['status']['state'],'WAITING_FOR_JELLYFIN')
        self.fake.jf_movie=True
        self.assertTrue(self.status()['status']['jellyfinVerified'])
        self.assertFalse(self.status(None)['status']['jellyfinVerified'])
    def test_upstream_failures_degrade_without_fabricated_progress(self):
        for service in ['radarr','qbit','jellyfin']:
            self.fake.fail={service}
            value=self.status()
            self.assertIn(service.upper()+'_UNAVAILABLE',value['issues'])
            self.assertFalse(value['status']['jellyfinVerified'])
    def test_unmatched_movie_never_uses_other_queue(self):
        self.fake.movie['tmdbId']=202
        value=self.status()
        self.assertIsNone(value['status']['progress'])
        self.assertNotIn(('qbit','api/v2/torrents/info'),self.fake.calls)
    def test_unrelated_episode_queue_not_applied(self):
        self.fake.series=True;self.fake.episodes[6]['id']=99
        self.assertIsNone(self.status()['episodes'][6]['status']['progress'])

class SecureHttp(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory=tempfile.TemporaryDirectory();d=Path(cls.directory.name)
        cls.cert=d/'cert.pem';cls.key=d/'key.pem'
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(cls.key),'-out',str(cls.cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cls.cert,cls.key)
        cls.http=ThreadingHTTPServer(('127.0.0.1',0),handler(Bridge(Fake(),lambda:NOW)))
        cls.http.socket=context.wrap_socket(cls.http.socket,server_side=True)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True);cls.thread.start()
        cls.trust=ssl.create_default_context(cafile=str(cls.cert))
    @classmethod
    def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();cls.directory.cleanup()
    def get(self,path,auth=AUTH):
        request=urllib.request.Request(f'https://localhost:{self.http.server_port}'+path,headers={'Authorization':auth})
        return urllib.request.urlopen(request,context=self.trust,timeout=4)
    def test_tls_authenticated_batch_endpoint(self):
        with self.get('/api/v1/requests/status?ids=1') as response:
            self.assertEqual(response.status,200);self.assertEqual(json.load(response)['requests'][0]['requestId'],1)
    def test_auth_ownership_bounds_and_readonly_methods(self):
        for path,auth,status in [('/api/v1/health','',401),('/api/v1/requests/2/status',AUTH,404),('/api/v1/requests/status?ids=-1',AUTH,400),('/api/v1/requests/status?ids='+','.join(map(str,range(1,27))),AUTH,400)]:
            with self.assertRaises(urllib.error.HTTPError) as error:self.get(path,auth)
            self.assertEqual(error.exception.code,status)
    def test_client_trust_is_required(self):
        with self.assertRaises(urllib.error.URLError):
            urllib.request.urlopen(f'https://localhost:{self.http.server_port}/api/v1/health',timeout=4)
    def test_private_bind_only(self):
        for ip in ['0.0.0.0','8.8.8.8']:
            with self.assertRaises(ValueError):private_bind(ip)
        self.assertEqual(private_bind('100.91.172.18'),'100.91.172.18')


class WireIntegration(unittest.TestCase):
    def setUp(self):
        self.fake=Fake(); fake=self.fake
        class UpstreamHandler(__import__('http.server',fromlist=['BaseHTTPRequestHandler']).BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_POST(self):
                if self.path != '/qbit/api/v2/auth/login':self.send_error(405);return
                body=self.rfile.read(int(self.headers.get('Content-Length',0)))
                if body != b'username=synthetic&password=synthetic':self.send_error(401);return
                self.send_response(200);self.send_header('Set-Cookie','SID=synthetic; HttpOnly');self.end_headers();self.wfile.write(b'Ok.')
            def do_GET(self):
                service,path=self.path.strip('/').split('/',1)
                if service in {'radarr','sonarr'} and self.headers.get('X-Api-Key')!='synthetic':self.send_error(401);return
                if service=='seerr' and self.headers.get('Cookie')!='connect.sid=synthetic':self.send_error(401);return
                if service=='qbit':
                    if self.headers.get('Cookie')!='SID=synthetic' or 'hashes='+HASH not in path:self.send_error(403);return
                    value=fake.qb
                else:value=fake.get(service,path,dict(self.headers))
                self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(value).encode())
        self.server=ThreadingHTTPServer(('127.0.0.1',0),UpstreamHandler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        urls={s:f'http://127.0.0.1:{self.server.server_port}/{s}' for s in ['seerr','radarr','sonarr','qbit','jellyfin']}
        self.bridge=Bridge(Transport(urls,{'radarr_key':'synthetic','sonarr_key':'synthetic','qbit_username':'synthetic','qbit_password':'synthetic'}),lambda:NOW)
    def tearDown(self):self.server.shutdown();self.server.server_close()
    def test_real_http_adapters_movie_download(self):
        user,cookie=self.bridge.authenticate(AUTH)
        value=self.bridge.statuses(user,cookie,'synthetic',[1])['requests'][0]
        self.assertEqual(value['status']['progress'],.74)
        self.assertEqual(value['status']['downloadEta']['confidence'],'MEDIUM')
    def test_real_http_adapters_partial_series(self):
        self.fake.series=True
        user,cookie=self.bridge.authenticate(AUTH)
        value=self.bridge.statuses(user,cookie,'synthetic',[1])['requests'][0]
        self.assertEqual(value['seasons'][0]['availableEpisodes'],6)
        self.assertEqual(value['episodes'][6]['status']['progress'],.74)
    def test_private_upstream_credentials_never_use_environment_proxy(self):
        from unittest.mock import patch
        with patch.dict(os.environ, {'HTTP_PROXY':'http://127.0.0.1:9','http_proxy':'http://127.0.0.1:9','NO_PROXY':'','no_proxy':''}):
            transport=Transport(self.bridge.transport.urls,self.bridge.transport.secrets)
            self.assertEqual(transport.get('seerr','api/v1/auth/me',{'Cookie':'connect.sid=synthetic'})['id'],7)
    def test_transport_rejects_non_authentication_posts(self):
        with self.assertRaises(ValueError):self.bridge.transport.call('qbit','api/v2/torrents/pause',form={'hashes':HASH})

class CredentialFiles(unittest.TestCase):
    def test_missing_and_insecure_credentials_fail_closed(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ,{'CREDENTIALS_DIRECTORY':directory}):
            self.assertIsNone(credential('missing'))
            with self.assertRaises(RuntimeError):credential('missing',required=True)
            p=Path(directory)/'radarr_key';p.write_text('synthetic');p.chmod(0o644)
            with self.assertRaises(RuntimeError):credential('radarr_key')
            p.chmod(0o600);self.assertEqual(credential('radarr_key'),'synthetic')

if __name__=="__main__":unittest.main()
