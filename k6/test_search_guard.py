"""Safety regression tests; do not connect to the application."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('suite', Path(__file__).with_name('run-search-suite.py'))
suite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(suite)


class GuardTests(unittest.TestCase):
    def sample(self, **changes):
        return dict(requests=30, p95_ms=100, http_failure_rate=0, dropped_iterations=0, **changes)

    def test_slo_miss_is_not_a_safety_abort(self):
        guard = suite.Guard(0)
        sample = self.sample()
        sample['p95_ms'] = 700
        for now in range(5, 125, 5):
            self.assertIsNone(guard.reason(now, sample, [1.0]))

    def test_sustained_breach_and_recovery_reset(self):
        guard = suite.Guard(0)
        sample = self.sample()
        sample['p95_ms'] = 3000
        for now in range(5, 60, 5):
            self.assertIsNone(guard.reason(now, sample, [1.0]))
        self.assertIn('latency', guard.reason(60, sample, [1.0]))
        sample['p95_ms'] = 100
        self.assertIsNone(guard.reason(65, sample, [1.0]))
        sample['p95_ms'] = 3000
        self.assertIsNone(guard.reason(70, sample, [1.0]))
        self.assertIsNone(guard.reason(95, sample, [1.0]))
        self.assertIn('latency', guard.reason(100, sample, [1.0]))

    def test_error_boundary_and_immediate_unreliable_load(self):
        guard = suite.Guard(0)
        sample = self.sample()
        sample['http_failure_rate'] = .10
        self.assertIsNone(guard.reason(30, sample, [1.0]))
        self.assertIn('errors', guard.reason(60, sample, [1.0]))
        sample['dropped_iterations'] = 1
        self.assertIn('dropped', guard.reason(5, sample, [1.0]))
        self.assertIn('unavailable', suite.Guard(0).reason(5, self.sample(), []))

    def test_rolling_window_expires_old_slow_requests(self):
        window = suite.Window()
        for stamp, value in ((0, 10000), (31, 100), (32, 200)):
            window.samples['http_req_duration'].append((stamp, value))
        result = window.read(32)
        self.assertEqual(result['requests'], 2)
        self.assertEqual(result['p95_ms'], 195)

    def test_k6_nanosecond_timestamp(self):
        for fraction in ('1', '12', '123', '1234', '12345', '123456', '1234567', '12345678', '123456789'):
            for zone in ('+09:00', 'Z'):
                window = suite.Window()
                window.add({'type': 'Point', 'metric': 'http_req_duration', 'data': {
                    'time': '2026-10-26T15:20:20.' + fraction + zone, 'value': 123}})
                self.assertEqual(len(window.samples['http_req_duration']), 1)


if __name__ == '__main__':
    unittest.main()
