(() => {
  'use strict';

  // ---------- 공통 유틸 ----------
  const $ = (sel) => document.querySelector(sel);
  const nf = new Intl.NumberFormat('ko-KR');
  const compact = new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 });
  const won = (n) => `${nf.format(n)}원`;
  const pct = (n) => `${Number(n).toFixed(1)}%`;
  const count = (n) => `${nf.format(n)}개`;
  const pad = (n) => String(n).padStart(2, '0');
  const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const parseISO = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const todayISO = () => iso(new Date());
  const daysAgoISO = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return iso(d); };
  const numOrNull = (v) => (v === '' || v === undefined ? null : Number(v));
  // 날짜 칸에 연도를 입력하는 중이면(예: 0002년) 범위를 벗어나 invalid 상태다. 그때는 조회하지 않는다
  const validDates = (...inputs) => inputs.every((el) => el.checkValidity());
  const formValues = (form) => Object.fromEntries(new FormData(form));

  async function api(path, { method = 'GET', body } = {}) {
    const res = await fetch(path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    if (res.status === 204) return null;
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const error = new Error((data && (data.detail || data.message)) || `요청 실패 (${res.status})`);
      error.status = res.status;
      throw error;
    }
    return data;
  }

  let toastTimer;
  function toast(message) {
    const el = $('#toast');
    el.hidden = false;
    el.textContent = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 3500);
  }

  // 데이터는 항상 textContent로 넣는다 (innerHTML 금지)
  function clearRows(tbody) { while (tbody.firstChild) tbody.removeChild(tbody.firstChild); }
  function cell(row, text, cls) { const td = row.insertCell(); td.textContent = text; if (cls) td.className = cls; return td; }
  function numCell(row, value, format) { const td = cell(row, format(value), 'num'); if (value < 0) td.classList.add('neg'); return td; }
  function emptyRow(tbody, colSpan, text) { const td = tbody.insertRow().insertCell(); td.colSpan = colSpan; td.className = 'empty'; td.textContent = text; }
  // 저장 버튼을 요청이 끝날 때까지 막아, 두 번 눌러 같은 기록이 두 번 저장되지 않게 한다
  async function submitting(form, task) {
    if (form.dataset.busy) return;
    form.dataset.busy = '1';
    const buttons = [...form.querySelectorAll('button')];
    const disabledBefore = buttons.map((b) => b.disabled);
    buttons.forEach((b) => { b.disabled = true; }); // 저장 중에 취소를 눌러도 저장은 진행되므로 함께 잠근다
    try {
      await task();
    } finally {
      buttons.forEach((b, i) => { b.disabled = disabledBefore[i]; });
      delete form.dataset.busy;
    }
  }
  function linkButton(label, onClick, danger) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = danger ? 'link danger' : 'link';
    b.textContent = label;
    b.addEventListener('click', onClick);
    return b;
  }

  // ---------- 화면 전환 ----------
  const VIEWS = ['dashboard', 'products', 'sales', 'categories'];
  function showView(name) {
    VIEWS.forEach((v) => { $(`#view-${v}`).hidden = v !== name; });
    document.querySelectorAll('.tab').forEach((t) => {
      const selected = t.dataset.view === name;
      t.classList.toggle('active', selected);
      t.setAttribute('aria-current', selected ? 'page' : 'false');
    });
    if (name === 'dashboard') loadDashboard();
    if (name === 'products') { loadCategories(); loadProducts(); }
    if (name === 'sales') loadProducts().then(loadSales);
    if (name === 'categories') loadCategories();
  }
  document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => showView(t.dataset.view)));

  // ---------- 대시보드 ----------
  const COLORS = {
    revenue: '#2a78d6', revenueHover: '#5598e7',   // 계열 1 (파랑)
    profit: '#eb6834', profitHover: '#f08a60',     // 계열 2 (주황)
    ink2: '#52514e', muted: '#898781', grid: '#e1e0d9', axis: '#c3c2b7',
  };
  const PERIOD_NAMES = { DAILY: '일별', WEEKLY: '주별', MONTHLY: '월별', YEARLY: '연도별' };
  // 원 그래프 조각: 같은 파란색의 진하기 차이로 구분한다. 큰 조각일수록 진해서 색을 구분하기 어려운 사람도 읽을 수 있다
  const PIE_SHADES = ['#0d366b', '#184f95', '#256abf', '#2a78d6', '#3987e5', '#6da7ec']; // 진한 순서 = 매출 큰 순서
  const PIE_OTHER = '#898781';   // 묶어 놓은 '기타'는 회색
  const SURFACE = '#fcfcfb';     // 조각 사이를 벌리는 배경색 테두리
  const OTHER = 'other';
  const dash = { period: 'DAILY', shownPeriod: 'DAILY', chart: null, points: [], categoryChart: null, slices: [],
    from: null, to: null, seq: 0 };

  const categoryLabel = (c) => c.categoryName ?? '미분류';
  const sharePct = (value, total) => (total > 0 ? `${((value / total) * 100).toFixed(1)}%` : '0.0%');

  async function loadDashboard() {
    if (!validDates($('#dash-from'), $('#dash-to'))) return;
    const seq = ++dash.seq; // 늦게 도착한 옛 응답이 새 결과와 입력칸을 덮지 않게 한다
    const params = new URLSearchParams({ period: dash.period });
    if ($('#dash-from').value) params.set('from', $('#dash-from').value);
    if ($('#dash-to').value) params.set('to', $('#dash-to').value);
    const wraps = document.querySelectorAll('.chart-wrap'); // 막대·원 그래프 모두 흐리게
    wraps.forEach((w) => w.classList.add('loading'));
    try {
      const summary = await api(`/api/sales/summary?${params}`);
      if (seq !== dash.seq) return;
      $('#dash-from').value = summary.from;   // from/to 생략 시 서버 기본 범위를 입력칸에 반영
      $('#dash-to').value = summary.to;
      dash.from = summary.from;               // 조회가 거부되면 이 기간으로 되돌린다
      dash.to = summary.to;
      $('#chart-title').textContent = `${PERIOD_NAMES[summary.period]} 매출 · 이익`;
      renderTiles(summary);
      renderChart(summary);
      renderSummaryTable(summary);
      renderCategoryChart(summary);
      renderCategorySalesTable(summary);
    } catch (e) {
      if (seq !== dash.seq) return;
      toast(e.message);
      if (dash.from) { // 화면의 숫자는 지난 기간 그대로이므로 날짜칸도 그 기간으로 되돌린다
        $('#dash-from').value = dash.from;
        $('#dash-to').value = dash.to;
      }
    } finally {
      if (seq === dash.seq) wraps.forEach((w) => w.classList.remove('loading'));
    }
  }

  $('#period-buttons').addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-period]');
    if (!btn) return;
    dash.period = btn.dataset.period;
    $('#period-buttons').querySelectorAll('button').forEach((b) => {
      b.classList.toggle('active', b === btn);
      b.setAttribute('aria-pressed', String(b === btn));
    });
    $('#dash-from').value = '';
    $('#dash-to').value = '';
    loadDashboard();
  });
  ['#dash-from', '#dash-to'].forEach((sel) => $(sel).addEventListener('change', loadDashboard));

  function renderTiles(s) {
    $('#tile-revenue').textContent = won(s.totalRevenue);
    $('#tile-profit').textContent = won(s.totalProfit);
    $('#tile-profit').classList.toggle('neg', s.totalProfit < 0);
    $('#tile-quantity').textContent = count(s.totalQuantity);
    $('#tile-rate').textContent = s.totalRevenue > 0 ? pct((s.totalProfit / s.totalRevenue) * 100) : '–';
  }

  function tickLabel(point, period) {
    const d = parseISO(point.start);
    switch (period) {
      case 'DAILY': return `${d.getMonth() + 1}/${d.getDate()}`;
      case 'WEEKLY': return `${d.getMonth() + 1}/${d.getDate()}~`;
      case 'MONTHLY': return `${d.getFullYear()}.${pad(d.getMonth() + 1)}`;
      default: return String(d.getFullYear());
    }
  }
  function rangeLabel(point, period) {
    return period === 'DAILY' ? point.start : `${point.label} (${point.start} ~ ${point.end})`;
  }

  function renderChart(s) {
    dash.points = s.points;
    dash.shownPeriod = s.period;
    const labels = s.points.map((p) => tickLabel(p, s.period));
    const revenue = s.points.map((p) => p.revenue);
    const profit = s.points.map((p) => p.profit);

    if (dash.chart) {
      dash.chart.data.labels = labels;
      dash.chart.data.datasets[0].data = revenue;
      dash.chart.data.datasets[1].data = profit;
      dash.chart.update();
      return;
    }

    const bar = (label, data, color, hover) => ({
      label, data,
      backgroundColor: color, hoverBackgroundColor: hover,
      maxBarThickness: 24, borderRadius: 4, borderSkipped: 'start',
    });
    dash.chart = new Chart($('#sales-chart'), {
      type: 'bar',
      data: {
        labels,
        datasets: [
          bar('매출', revenue, COLORS.revenue, COLORS.revenueHover),
          bar('이익', profit, COLORS.profit, COLORS.profitHover),
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },   // 한 툴팁에 모든 계열
        datasets: { bar: { categoryPercentage: 0.7, barPercentage: 0.85 } },
        plugins: {
          legend: { position: 'top', align: 'start', labels: { boxWidth: 12, boxHeight: 12, color: COLORS.ink2 } },
          tooltip: {
            callbacks: {
              title: (items) => rangeLabel(dash.points[items[0].dataIndex], dash.shownPeriod),
              label: (item) => `${item.dataset.label}  ${won(item.parsed.y)}`,
            },
          },
        },
        scales: {
          x: { grid: { display: false }, border: { color: COLORS.axis }, ticks: { color: COLORS.muted, maxRotation: 0, autoSkip: true } },
          y: {
            beginAtZero: true,
            grid: { color: COLORS.grid },
            border: { display: false },
            ticks: { color: COLORS.muted, callback: (v) => compact.format(v) },
          },
        },
      },
    });
  }

  function renderSummaryTable(s) {
    const tbody = $('#summary-table tbody');
    clearRows(tbody);
    for (const p of s.points) {
      const tr = tbody.insertRow();
      cell(tr, rangeLabel(p, s.period));
      numCell(tr, p.revenue, won);
      numCell(tr, p.profit, won);
      cell(tr, count(p.quantity), 'num');
    }
    const total = tbody.insertRow();
    total.className = 'total';
    cell(total, '합계');
    numCell(total, s.totalRevenue, won);
    numCell(total, s.totalProfit, won);
    cell(total, count(s.totalQuantity), 'num');
  }

  // 매출이 있는 카테고리만, 많으면 작은 것들을 '기타'로 묶는다 (조각이 너무 얇으면 읽을 수 없다)
  function categorySlices(summary) {
    const rows = summary.categories.filter((c) => c.revenue > 0);
    if (rows.length <= PIE_SHADES.length) return rows; // 색을 줄 수 있는 만큼만 따로 보여 준다
    const tail = rows.slice(PIE_SHADES.length);
    return [...rows.slice(0, PIE_SHADES.length), {
      categoryId: OTHER,
      categoryName: tail.length === 1 ? categoryLabel(tail[0]) : `기타 ${tail.length}개`, // 하나뿐이면 이름 그대로
      revenue: tail.reduce((sum, c) => sum + c.revenue, 0),
      profit: tail.reduce((sum, c) => sum + c.profit, 0),
      quantity: tail.reduce((sum, c) => sum + c.quantity, 0),
    }];
  }

  function renderCategoryChart(summary) {
    const slices = categorySlices(summary);
    dash.slices = slices;
    $('#category-chart').parentElement.hidden = slices.length === 0;
    $('#category-empty').hidden = slices.length > 0;
    if (!slices.length) return;

    const total = slices.reduce((sum, c) => sum + c.revenue, 0);
    const labels = slices.map((c) => `${categoryLabel(c)} ${sharePct(c.revenue, total)}`);
    const data = slices.map((c) => c.revenue);
    const colors = slices.map((c, i) => (c.categoryId === OTHER ? PIE_OTHER : PIE_SHADES[i]));

    if (dash.categoryChart) {
      dash.categoryChart.data.labels = labels;
      dash.categoryChart.data.datasets[0].data = data;
      dash.categoryChart.data.datasets[0].backgroundColor = colors;
      dash.categoryChart.update();
      return;
    }
    dash.categoryChart = new Chart($('#category-chart'), {
      type: 'pie',
      data: { labels, datasets: [{ data, backgroundColor: colors, borderColor: SURFACE, borderWidth: 2, hoverOffset: 6 }] },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right', onClick: () => {}, // 조각을 숨기면 라벨의 비중과 그림이 어긋난다
            labels: { boxWidth: 12, boxHeight: 12, padding: 10, color: COLORS.ink2 } },
          tooltip: {
            callbacks: {
              title: (items) => categoryLabel(dash.slices[items[0].dataIndex]),
              label: (item) => {
                const c = dash.slices[item.dataIndex];
                const total2 = dash.slices.reduce((sum, x) => sum + x.revenue, 0);
                return [`매출  ${won(c.revenue)} (${sharePct(c.revenue, total2)})`, `이익  ${won(c.profit)}`, `수량  ${count(c.quantity)}`];
              },
            },
          },
        },
      },
    });
  }

  function renderCategorySalesTable(summary) {
    const tbody = $('#category-sales-table tbody');
    clearRows(tbody);
    const rows = summary.categories; // 서버는 판매가 있는 카테고리만 준다. 매출 0원이어도 이익·수량이 있으니 모두 보여 준다
    if (!rows.length) { emptyRow(tbody, 5, '이 기간에는 판매 기록이 없습니다.'); return; }
    for (const c of rows) {
      const tr = tbody.insertRow();
      cell(tr, categoryLabel(c), c.categoryName ? '' : 'muted');
      numCell(tr, c.revenue, won);
      cell(tr, sharePct(c.revenue, summary.totalRevenue), 'num');
      numCell(tr, c.profit, won);
      cell(tr, count(c.quantity), 'num');
    }
    const total = tbody.insertRow();
    total.className = 'total';
    cell(total, '합계');
    numCell(total, summary.totalRevenue, won);
    cell(total, sharePct(summary.totalRevenue, summary.totalRevenue), 'num');
    numCell(total, summary.totalProfit, won);
    cell(total, count(summary.totalQuantity), 'num');
  }

  // ---------- 상품 ----------
  const productForm = $('#product-form');
  const PRODUCT_FIELDS = ['name', 'sellingPrice', 'costPrice', 'buyerShippingFee', 'shippingCost', 'otherCost',
    'orderFeeRate', 'salesFeeRate'];
  const PREVIEW_IDLE = '판매가와 원가를 입력하면 수수료·마진이 계산됩니다.';
  let products = [];
  let editingProductId = null;

  async function loadProducts() {
    try {
      products = await api('/api/products');
    } catch (e) {
      toast(e.message);
      return;
    }
    renderProductTable();
    renderProductSelect();
  }

  function renderProductTable() {
    const tbody = $('#product-table tbody');
    clearRows(tbody);
    if (!products.length) { emptyRow(tbody, 12, '등록된 상품이 없습니다. 위에서 상품을 등록하세요.'); return; }
    for (const p of products) {
      const tr = tbody.insertRow();
      cell(tr, p.name);
      cell(tr, p.categoryName ?? '미분류', p.categoryName ? '' : 'muted');
      numCell(tr, p.sellingPrice, won);
      numCell(tr, p.costPrice, won);
      numCell(tr, p.buyerShippingFee, won);
      numCell(tr, p.shippingCost, won);
      numCell(tr, p.otherCost, won);
      cell(tr, `${Number(p.orderFeeRate).toFixed(2)} + ${Number(p.salesFeeRate).toFixed(2)}%`, 'num');
      numCell(tr, p.fee, won);
      numCell(tr, p.margin, won);
      numCell(tr, Number(p.marginRate), pct);
      cell(tr, '', 'row-actions').append(
        linkButton('수정', () => startEditProduct(p)),
        linkButton('삭제', () => deleteProduct(p), true),
      );
    }
  }

  function productBody() {
    const v = formValues(productForm);
    return {
      name: v.name.trim(),
      categoryId: numOrNull(v.categoryId),
      sellingPrice: Number(v.sellingPrice),
      costPrice: Number(v.costPrice),
      shippingCost: numOrNull(v.shippingCost),
      buyerShippingFee: numOrNull(v.buyerShippingFee),
      otherCost: numOrNull(v.otherCost),
      orderFeeRate: numOrNull(v.orderFeeRate),
      salesFeeRate: numOrNull(v.salesFeeRate),
    };
  }

  productForm.addEventListener('submit', (e) => {
    e.preventDefault();
    submitting(productForm, async () => {
      const editing = editingProductId; // 저장하는 동안 수정 상태가 바뀔 수 있으므로 지금 값을 쓴다
      const body = productBody();
      try {
        if (editing) await api(`/api/products/${editing}`, { method: 'PUT', body });
        else await api('/api/products', { method: 'POST', body });
        toast(editing ? '상품을 수정했습니다.' : '상품을 등록했습니다.');
        if (editingProductId === editing) resetProductForm();
      } catch (err) {
        toast(err.message);
        if (err.status === 404 && editingProductId === editing) resetProductForm(); // 이미 지워진 상품
      }
      loadProducts(); // 실패했을 때도 목록을 최신으로 맞춘다
    });
  });

  function startEditProduct(p) {
    editingProductId = p.id;
    $('#product-form-title').textContent = `상품 수정 · ${p.name}`;
    PRODUCT_FIELDS.forEach((k) => { productForm.elements[k].value = p[k]; });
    // 카테고리 목록을 아직 못 받았거나 다른 창에서 막 만든 카테고리여도, 저장할 때 상품의 카테고리가 지워지지 않게 한다
    const categorySelect = productForm.elements.categoryId;
    if (p.categoryId != null && ![...categorySelect.options].some((o) => o.value === String(p.categoryId))) {
      categorySelect.append(new Option(p.categoryName, p.categoryId));
    }
    categorySelect.value = p.categoryId ?? '';
    $('#product-cancel').hidden = false;
    updateMarginPreview();
    productForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetProductForm() {
    editingProductId = null;
    productForm.reset();
    $('#product-form-title').textContent = '상품 등록';
    $('#product-cancel').hidden = true;
    $('#margin-preview').textContent = PREVIEW_IDLE;
    $('#margin-preview').classList.remove('neg');
  }
  $('#product-cancel').addEventListener('click', resetProductForm);

  async function deleteProduct(p) {
    if (!window.confirm(`'${p.name}' 상품을 삭제할까요?`)) return;
    try {
      await api(`/api/products/${p.id}`, { method: 'DELETE' });
      toast('상품을 삭제했습니다.');
      if (editingProductId === p.id) resetProductForm();
    } catch (err) {
      toast(err.message);
      if (err.status === 404 && editingProductId === p.id) resetProductForm();
    }
    loadProducts();
  }

  // 입력할 때마다 서버의 계산식(/api/margin)으로 마진 미리보기
  let previewTimer;
  productForm.addEventListener('input', () => {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(updateMarginPreview, 250);
  });
  async function updateMarginPreview() {
    const el = $('#margin-preview');
    const v = formValues(productForm);
    if (v.sellingPrice === '' || v.costPrice === '') { el.textContent = PREVIEW_IDLE; el.classList.remove('neg'); return; }
    try {
      const { name, categoryId, ...body } = productBody();
      const m = await api('/api/margin', { method: 'POST', body });
      el.textContent = `수수료 ${won(m.fee)} · 개당 마진 ${won(m.margin)} · 마진율 ${pct(m.marginRate)}`;
      el.classList.toggle('neg', m.margin < 0);
    } catch (err) {
      el.textContent = err.message;
    }
  }

  // ---------- 카테고리 ----------
  const categoryForm = $('#category-form');
  let categories = [];
  let editingCategoryId = null;

  async function loadCategories() {
    try {
      categories = await api('/api/categories');
    } catch (e) {
      toast(e.message);
      return;
    }
    renderCategoryTable();
    renderCategorySelect();
  }

  function renderCategoryTable() {
    const tbody = $('#category-table tbody');
    clearRows(tbody);
    if (!categories.length) { emptyRow(tbody, 3, '카테고리가 없습니다. 위에서 추가하세요.'); return; }
    for (const c of categories) {
      const tr = tbody.insertRow();
      cell(tr, c.name);
      cell(tr, count(c.productCount), 'num');
      cell(tr, '', 'row-actions').append(
        linkButton('수정', () => startEditCategory(c)),
        linkButton('삭제', () => deleteCategory(c), true),
      );
    }
  }

  // 상품 등록 폼의 카테고리 선택지. 고르던 값은 유지하고, 지워진 카테고리면 미분류로 돌아간다
  function renderCategorySelect() {
    const select = productForm.elements.categoryId;
    const current = select.value;
    while (select.firstChild) select.removeChild(select.firstChild);
    select.append(new Option('미분류', ''));
    for (const c of categories) select.append(new Option(c.name, c.id));
    if (categories.some((c) => String(c.id) === current)) select.value = current;
  }

  categoryForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const body = { name: formValues(categoryForm).name.trim() };
    submitting(categoryForm, async () => {
      const editing = editingCategoryId;
      try {
        if (editing) await api(`/api/categories/${editing}`, { method: 'PUT', body });
        else await api('/api/categories', { method: 'POST', body });
        toast(editing ? '카테고리를 수정했습니다.' : '카테고리를 추가했습니다.');
        if (editingCategoryId === editing) resetCategoryForm();
      } catch (err) {
        toast(err.message);
        if (err.status === 404 && editingCategoryId === editing) resetCategoryForm();
      }
      loadCategories();
    });
  });

  function startEditCategory(c) {
    editingCategoryId = c.id;
    $('#category-form-title').textContent = `카테고리 수정 · ${c.name}`;
    categoryForm.elements.name.value = c.name;
    $('#category-cancel').hidden = false;
    categoryForm.elements.name.focus();
  }

  function resetCategoryForm() {
    editingCategoryId = null;
    categoryForm.reset();
    $('#category-form-title').textContent = '카테고리 추가';
    $('#category-cancel').hidden = true;
  }
  $('#category-cancel').addEventListener('click', resetCategoryForm);

  async function deleteCategory(c) {
    const note = c.productCount > 0 ? `\n이 카테고리의 상품 ${count(c.productCount)}는 '미분류'로 바뀝니다.` : '';
    if (!window.confirm(`'${c.name}' 카테고리를 삭제할까요?${note}`)) return;
    try {
      await api(`/api/categories/${c.id}`, { method: 'DELETE' });
      toast('카테고리를 삭제했습니다.');
      if (editingCategoryId === c.id) resetCategoryForm();
    } catch (err) {
      toast(err.message);
      if (err.status === 404 && editingCategoryId === c.id) resetCategoryForm();
    }
    loadCategories();
  }

  // ---------- 판매 기록 ----------
  const saleForm = $('#sale-form');
  let editingSaleId = null;
  let salesSeq = 0;

  function renderProductSelect() {
    const select = saleForm.elements.productId;
    const current = select.value;
    while (select.firstChild) select.removeChild(select.firstChild);
    if (!products.length) { select.append(new Option('먼저 상품을 등록하세요', '')); return; }
    for (const p of products) select.append(new Option(`${p.name} (${won(p.sellingPrice)})`, p.id));
    if (current && products.some((p) => String(p.id) === current)) select.value = current;
    else if (!editingSaleId) syncUnitPrice();
  }

  function syncUnitPrice() {
    const p = products.find((x) => String(x.id) === saleForm.elements.productId.value);
    if (p) saleForm.elements.unitPrice.value = p.sellingPrice;
  }
  saleForm.elements.productId.addEventListener('change', syncUnitPrice);

  async function loadSales() {
    const fromInput = $('#sales-from'), toInput = $('#sales-to');
    if (!fromInput.value) fromInput.value = daysAgoISO(29); // 비우면 최근 30일만 나오므로 그 기간을 칸에 보여 준다
    if (!toInput.value) toInput.value = todayISO();
    if (!validDates(fromInput, toInput)) return;
    const seq = ++salesSeq;
    const params = new URLSearchParams();
    if ($('#sales-from').value) params.set('from', $('#sales-from').value);
    if ($('#sales-to').value) params.set('to', $('#sales-to').value);
    let sales;
    try {
      sales = await api(`/api/sales?${params}`);
    } catch (e) {
      if (seq === salesSeq) toast(e.message);
      return;
    }
    if (seq !== salesSeq) return;
    const tbody = $('#sale-table tbody');
    clearRows(tbody);
    if (!sales.length) { emptyRow(tbody, 7, '이 기간의 판매 기록이 없습니다.'); return; }
    for (const s of sales) {
      const tr = tbody.insertRow();
      cell(tr, s.saleDate);
      cell(tr, s.productName);
      cell(tr, count(s.quantity), 'num');
      numCell(tr, s.unitPrice, won);
      numCell(tr, s.revenue, won);
      numCell(tr, s.profit, won);
      cell(tr, '', 'row-actions').append(
        linkButton('수정', () => startEditSale(s)),
        linkButton('삭제', () => deleteSale(s), true),
      );
    }
  }

  saleForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const v = formValues(saleForm);
    const body = { productId: Number(v.productId), saleDate: v.saleDate, quantity: Number(v.quantity), unitPrice: numOrNull(v.unitPrice) };
    submitting(saleForm, async () => {
      const editing = editingSaleId;
      try {
        if (editing) await api(`/api/sales/${editing}`, { method: 'PUT', body });
        else await api('/api/sales', { method: 'POST', body });
        toast(editing ? '판매 기록을 수정했습니다.' : '판매 기록을 추가했습니다.');
        if (editingSaleId === editing) resetSaleForm();
      } catch (err) {
        toast(err.message);
        if (err.status === 404 && editingSaleId === editing) resetSaleForm(); // 다른 창에서 이미 지운 기록
      }
      loadSales();
    });
  });

  function startEditSale(s) {
    editingSaleId = s.id;
    $('#sale-form-title').textContent = `판매 기록 수정 · ${s.saleDate} ${s.productName}`;
    saleForm.elements.productId.value = s.productId;
    saleForm.elements.saleDate.value = s.saleDate;
    saleForm.elements.quantity.value = s.quantity;
    saleForm.elements.unitPrice.value = s.unitPrice;
    $('#sale-cancel').hidden = false;
    saleForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetSaleForm() {
    editingSaleId = null;
    saleForm.reset();
    saleForm.elements.saleDate.value = todayISO();
    $('#sale-form-title').textContent = '판매 기록 추가';
    $('#sale-cancel').hidden = true;
    syncUnitPrice();
  }
  $('#sale-cancel').addEventListener('click', resetSaleForm);

  async function deleteSale(s) {
    if (!window.confirm(`${s.saleDate} '${s.productName}' ${count(s.quantity)} 판매 기록을 삭제할까요?`)) return;
    try {
      await api(`/api/sales/${s.id}`, { method: 'DELETE' });
      toast('판매 기록을 삭제했습니다.');
      if (editingSaleId === s.id) resetSaleForm();
    } catch (err) {
      toast(err.message);
      if (err.status === 404 && editingSaleId === s.id) resetSaleForm();
    }
    loadSales();
  }

  saleForm.elements.saleDate.max = todayISO(); // 미래 날짜로 저장하면 기본 화면에서 보이지 않는다
  $('#sales-from').value = daysAgoISO(29);
  $('#sales-to').value = todayISO();
  ['#sales-from', '#sales-to'].forEach((sel) => $(sel).addEventListener('change', loadSales));

  // ---------- 상호 표시 (서버 설정에서 읽음) ----------
  async function loadSettings() {
    try {
      const settings = await api('/api/settings');
      if (settings.storeName) {
        $('#brand').textContent = settings.storeName;
        document.title = `${settings.storeName} 매출·마진 관리`;
      }
    } catch (e) {
      // 설정을 못 읽으면 기본 이름 유지
    }
  }

  // ---------- 시작 ----------
  loadSettings();
  Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif';
  Chart.defaults.color = COLORS.ink2;
  resetSaleForm();
  loadCategories();
  loadProducts().then(() => showView('dashboard'));
})();
