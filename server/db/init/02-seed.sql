INSERT INTO institution (
    id,
    institution_code,
    institution_name,
    account_number,
    wallet_address,
    encrypted_private_key,
    enode_url,
    rpc_endpoint
)
VALUES
(
    1,
    'CB',
    'Central Bank',
    '100-000-000001',
    '0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73',
    'v1:4PXXe9EEJYkWJJXU:JbMb+VwV9v9bhqZIl9dj8VLxSxWm7UDEUKMK0S1mEVLrEsSKZyXGqpZJPoxj5DxG0qKR81WAUDLfrSu0FuPi7/eHYaNIHMi1Tfbp+WO7T5E=',
    'enode://7fa5133d55c65f610f8a75a69a6dcd35e5a3bf26b23c070e4ce43e578daea0e2689b1b1938a44c32bd1cd71b143b420a007052ddb5048428ddaf99a21e75632f@172.16.239.11:30303',
    'http://localhost:8545'
),
(
    2,
    'KB',
    'Commercial Bank 1',
    '200-000-000001',
    '0x627306090abaB3A6e1400e9345bC60c78a8BEf57',
    'v1:Qx7PqVImcRzlnYsF:s7cb1s/4x1B3+N3zMxjxq9gyNtLLhMnnChYotKsQjdiIXwm2qlati81hRxnazXBKNYCxtN5R3iLzHfUTxJUVoCfWtwpOjB6osRb20RvO+UM=',
    'enode://2b48a77f024713797162d9257b3824cf32cb232d57c243b27deab4e6715917d594b0a666ac639f096be1eea5b195369eb5803b89bf7343c2d419fa0bf50e724b@172.16.239.12:30303',
    'http://localhost:8547'
),
(
    3,
    'SH',
    'Commercial Bank 2',
    '300-000-000001',
    '0xf17f52151EbEF6C7334FAD080c5704D77216b732',
    'v1:4zb8quFXmqxwhcZI:jtdo1aQR91S6lMw2J5QeS2kbF9n5ruCIX53lWB/TKKAw0B+WOLORTi1soclSocTk73MMBHqDLzMXt6Yk2xvikz0bLnmAEC4tz06QvNdrT+s=',
    'enode://939642618c06dac18b0028d795a1b5181c6de8364bb133dff49e5da2baf113bb4ea61c741fd726526d49292b67bdca3599f886223d8870beca6d497ad2f9641c@172.16.239.13:30303',
    'http://localhost:8549'
),
(
    4,
    'NH',
    'Commercial Bank 3',
    '400-000-000001',
    '0xE9BA79E62a58225065bF24313896CD332dAFCB3C',
    'v1:+HAYoIqwCkzhHf1s:WdqlbW4V00Yc2eu538WCHsxQOtKpSZyrO8OxZ3b1o1L74ssGfVX1BzBfgia3G0KFDa57irF9+0uRUUugE9hR+mzHfx5y+3+qcG1kbe1qUE4=',
    'enode://29c55b7ab10d198407fb2338e1f8634f93189caab3a4447cf71bf7f2bfedac92820afc0849f996847255d4e2da6a755486af76516babce4af41dd3c23fdaf0d6@172.16.239.14:30303',
    'http://localhost:8551'
)
ON DUPLICATE KEY UPDATE
    institution_name = VALUES(institution_name),
    account_number = VALUES(account_number),
    wallet_address = VALUES(wallet_address),
    encrypted_private_key = VALUES(encrypted_private_key),
    enode_url = VALUES(enode_url),
    rpc_endpoint = VALUES(rpc_endpoint);