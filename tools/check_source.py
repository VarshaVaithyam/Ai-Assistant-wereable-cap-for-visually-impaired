"""Host-only packaging checks, NOT Flutter/firmware compilation or hardware tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent

class PackagingTests(unittest.TestCase):
    def test_http_is_scoped_to_camera(self):
        xml = ET.parse(ROOT / 'platform/android/res/xml/network_security_config.xml')
        self.assertEqual(xml.find('base-config').attrib['cleartextTrafficPermitted'], 'false')
        domains = xml.findall('./domain-config/domain')
        self.assertEqual([x.text for x in domains], ['192.168.4.1'])
        self.assertEqual(domains[0].attrib['includeSubdomains'], 'false')

    def test_manifest_uses_security_config_without_unused_permissions(self):
        xml = ET.parse(ROOT / 'platform/android/AndroidManifest.xml')
        ns = '{http://schemas.android.com/apk/res/android}'
        permissions = {x.attrib[ns + 'name'] for x in xml.findall('uses-permission')}
        self.assertEqual(permissions, {'android.permission.INTERNET',
                                      'android.permission.ACCESS_NETWORK_STATE',
                                      'android.permission.CHANGE_NETWORK_STATE'})
        self.assertEqual(xml.find('application').attrib[ns + 'networkSecurityConfig'],
                         '@xml/network_security_config')
        self.assertIsNone(xml.find('application').get(ns + 'usesCleartextTraffic'))

    def test_firmware_contract(self):
        source = (ROOT / 'firmware/esp32_cam/esp32_cam.ino').read_text()
        for endpoint in ['/status', '/capture', '/stream']:
            self.assertIn(f'.uri = "{endpoint}"', source)
        self.assertIn('httpd_req_async_handler_begin', source)
        self.assertIn('httpd_req_async_handler_complete', source)
        self.assertIn('visionassist-esp32-cam', source)

if __name__ == '__main__':
    unittest.main()
