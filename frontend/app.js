const API_URL = window.VAULT_API_URL || 'http://localhost:8080/api';

const readFileAsText = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('Unable to read file'));
    reader.readAsText(file);
  });

const status = document.getElementById('status');
const setStatus = (msg) => {
  status.textContent = msg;
};

const getSharedPayload = async () => {
  const certFile = document.getElementById('certificate').files[0];
  const policyFile = document.getElementById('policy').files[0];
  const passphrase = document.getElementById('passphrase').value;

  if (!certFile || !policyFile || !passphrase) {
    throw new Error('Certificate, policy, and passphrase are required.');
  }

  const [certificatePem, policiesJson] = await Promise.all([
    readFileAsText(certFile),
    readFileAsText(policyFile)
  ]);

  return { certificatePem, policiesJson, passphrase };
};

const request = async (endpoint, payload) => {
  const response = await fetch(`${API_URL}${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.error || 'Request failed');
  }
  return body;
};

document.getElementById('putBtn').addEventListener('click', async () => {
  try {
    const shared = await getSharedPayload();
    const path = document.getElementById('putPath').value;
    const secret = document.getElementById('putSecret').value;
    await request('/put', { ...shared, path, secret });
    setStatus(`Stored secret at ${path}`);
  } catch (error) {
    setStatus(error.message);
  }
});

document.getElementById('getBtn').addEventListener('click', async () => {
  try {
    const shared = await getSharedPayload();
    const path = document.getElementById('getPath').value;
    const result = await request('/get', { ...shared, path });
    document.getElementById('getResult').value = result.secret;
    setStatus(`Fetched secret at ${path}`);
  } catch (error) {
    setStatus(error.message);
  }
});

document.getElementById('listBtn').addEventListener('click', async () => {
  try {
    const shared = await getSharedPayload();
    const prefix = document.getElementById('listPrefix').value;
    const result = await request('/list', { ...shared, prefix });
    const listElement = document.getElementById('listResult');
    listElement.innerHTML = '';
    result.items.forEach((item) => {
      const li = document.createElement('li');
      li.textContent = item;
      listElement.appendChild(li);
    });
    setStatus(`Found ${result.items.length} secret path(s).`);
  } catch (error) {
    setStatus(error.message);
  }
});

for (const button of document.querySelectorAll('.menu-item')) {
  button.addEventListener('click', () => {
    document.querySelectorAll('.menu-item').forEach((item) => item.classList.remove('active'));
    button.classList.add('active');
    const panelName = button.dataset.panel;
    document.querySelectorAll('.panel').forEach((panel) => {
      panel.classList.toggle('hidden', panel.dataset.panel !== panelName);
    });
  });
}
