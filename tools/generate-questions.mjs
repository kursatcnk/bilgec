import fs from "node:fs";
import path from "node:path";
import initSqlJs from "sql.js";
import sharp from "sharp";
import { manualQuestions } from "./manual-questions.mjs";

const root = path.resolve(import.meta.dirname, "..");
const countries = JSON.parse(fs.readFileSync(path.join(root, "data/sources/countries.json"), "utf8"))
  .filter((country) => country.unMember || ["PS", "VA"].includes(country.cca2))
  .sort((a, b) => a.cca2.localeCompare(b.cca2));
const elements = JSON.parse(fs.readFileSync(path.join(root, "data/sources/periodic-table.json"), "utf8"))
  .elements.filter((element) => element.number <= 118);

if (countries.length !== 195) throw new Error(`195 ülke bekleniyordu, ${countries.length} bulundu.`);
if (elements.length !== 118) throw new Error(`118 element bekleniyordu, ${elements.length} bulundu.`);
if (manualQuestions.length !== 200) throw new Error(`200 elle hazırlanmış soru bekleniyordu, ${manualQuestions.length} bulundu.`);

const capitalNames = {
  "Abu Dhabi": "Abu Dabi", "Athens": "Atina", "Baghdad": "Bağdat", "Beijing": "Pekin",
  "Belgrade": "Belgrad", "Brussels": "Brüksel", "Bucharest": "Bükreş", "Cairo": "Kahire",
  "Copenhagen": "Kopenhag", "Damascus": "Şam", "Helsinki": "Helsinki", "Lisbon": "Lizbon",
  "London": "Londra", "Moscow": "Moskova", "Nicosia": "Lefkoşa", "Prague": "Prag",
  "Rome": "Roma", "Sofia": "Sofya", "Tbilisi": "Tiflis", "Tehran": "Tahran",
  "Vienna": "Viyana", "Warsaw": "Varşova", "Yerevan": "Erivan",
};
const continentNames = { Africa: "Afrika", Americas: "Amerika", Asia: "Asya", Europe: "Avrupa", Oceania: "Okyanusya", Antarctic: "Antarktika" };
const elementCategoryNames = {
  "diatomic nonmetal": "İki atomlu ametal",
  "noble gas": "Soy gaz",
  "alkali metal": "Alkali metal",
  "alkaline earth metal": "Toprak alkali metal",
  metalloid: "Yarı metal",
  "polyatomic nonmetal": "Çok atomlu ametal",
  "post-transition metal": "Geçiş sonrası metal",
  "transition metal": "Geçiş metali",
  lanthanide: "Lantanit",
  actinide: "Aktinit",
  "unknown, probably transition metal": "Muhtemel geçiş metali",
  "unknown, probably post-transition metal": "Muhtemel geçiş sonrası metal",
  "unknown, probably metalloid": "Muhtemel yarı metal",
  "unknown, predicted to be noble gas": "Muhtemel soy gaz",
  "unknown, but predicted to be an alkali metal": "Muhtemel alkali metal",
};
const phaseNames = { Gas: "Gaz", Solid: "Katı", Liquid: "Sıvı" };
const elementNames = [
  "Hidrojen", "Helyum", "Lityum", "Berilyum", "Bor", "Karbon", "Azot", "Oksijen", "Flor", "Neon",
  "Sodyum", "Magnezyum", "Alüminyum", "Silisyum", "Fosfor", "Kükürt", "Klor", "Argon", "Potasyum", "Kalsiyum",
  "Skandiyum", "Titanyum", "Vanadyum", "Krom", "Manganez", "Demir", "Kobalt", "Nikel", "Bakır", "Çinko",
  "Galyum", "Germanyum", "Arsenik", "Selenyum", "Brom", "Kripton", "Rubidyum", "Stronsiyum", "İtriyum", "Zirkonyum",
  "Niyobyum", "Molibden", "Teknesyum", "Rutenyum", "Rodyum", "Paladyum", "Gümüş", "Kadmiyum", "İndiyum", "Kalay",
  "Antimon", "Tellür", "İyot", "Ksenon", "Sezyum", "Baryum", "Lantan", "Seryum", "Praseodim", "Neodim",
  "Prometyum", "Samaryum", "Evropiyum", "Gadolinyum", "Terbiyum", "Disprozyum", "Holmiyum", "Erbiyum", "Tulyum", "İterbiyum",
  "Lutesyum", "Hafniyum", "Tantal", "Tungsten", "Renyum", "Osmiyum", "İridyum", "Platin", "Altın", "Cıva",
  "Talyum", "Kurşun", "Bizmut", "Polonyum", "Astatin", "Radon", "Fransiyum", "Radyum", "Aktinyum", "Toryum",
  "Protaktinyum", "Uranyum", "Neptünyum", "Plütonyum", "Amerikyum", "Küriyum", "Berkelyum", "Kaliforniyum", "Einsteinyum", "Fermiyum",
  "Mendelevyum", "Nobelyum", "Lavrensiyum", "Rutherfordiyum", "Dubniyum", "Seaborgiyum", "Bohriyum", "Hassiyum", "Meitneriyum", "Darmstadtiyum",
  "Röntgenyum", "Kopernikyum", "Nihonyum", "Flerovyum", "Moskovyum", "Livermoryum", "Tennesin", "Oganesson",
];

const countryName = (country) => country.translations?.tur?.common || country.name.common;
const officialCountryName = (country) => country.translations?.tur?.official || country.name.official;
const capitalName = (country) => capitalNames[country.capital?.[0]] || country.capital?.[0];
const continentName = (country) => continentNames[country.region] || country.region;
const countryByCode = new Map(countries.map((country) => [country.cca3, country]));

let nextId = 1;
const questions = [];
function hash(text) {
  let value = 2166136261;
  for (const char of text) value = Math.imul(value ^ char.charCodeAt(0), 16777619);
  return value >>> 0;
}
function shuffled(values, seed) {
  const copy = [...new Set(values)];
  let state = hash(seed) || 1;
  for (let index = copy.length - 1; index > 0; index--) {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    const target = state % (index + 1);
    [copy[index], copy[target]] = [copy[target], copy[index]];
  }
  return copy;
}
function addQuestion({ category, prompt, correct, wrong, explanation, factKey, imageAsset = null, reviewed = true }) {
  const options = shuffled([correct, ...wrong.filter((item) => item !== correct)], `${factKey}:${prompt}`).slice(0, 4);
  if (options.length !== 4) throw new Error(`Dört benzersiz seçenek üretilemedi: ${prompt}`);
  questions.push({
    id: nextId++, category, prompt, options, correctIndex: options.indexOf(correct), explanation,
    factKey, imageAsset, reviewed,
  });
}
function wrongFrom(values, correct, seed, forbidden = []) {
  return shuffled(values.filter((value) => value && value !== correct && !forbidden.includes(value)), seed).slice(0, 3);
}

const names = countries.map(countryName);
const capitals = countries.map(capitalName).filter(Boolean);
const continents = [...new Set(countries.map(continentName))];
const currencyCodes = [...new Set(countries.flatMap((country) => Object.keys(country.currencies || {})))];
const callingCodes = countries.map((country) => country.idd?.root && country.idd?.suffixes?.length === 1 ? `${country.idd.root}${country.idd.suffixes[0]}` : null).filter(Boolean);
const tlds = countries.map((country) => country.tld?.[0]).filter(Boolean);
const officialNames = countries.map(officialCountryName);

for (const country of countries) {
  const name = countryName(country);
  const continent = continentName(country);
  const regionalCountries = countries.filter((item) => item.region === country.region).map(countryName);
  const flagWrong = wrongFrom(regionalCountries.length >= 4 ? regionalCountries : names, name, `flag:${country.cca2}`);
  for (const prompt of [
    "Görseldeki bayrak hangi ülkeye aittir?",
    "Bu bayrak hangi ülkeyi temsil eder?",
    "Ekrandaki bayrağın ülkesi hangisidir?",
    "Bu ulusal bayrak hangi seçenekte doğru verilmiştir?",
    "Gösterilen bayrağı kullanan ülke hangisidir?",
  ]) {
    addQuestion({ category: "Bayraklar", prompt, correct: name, wrong: flagWrong, explanation: `Görselde ${name} bayrağı yer alıyor.`, factKey: `flag:${country.cca2}`, imageAsset: `flags/${country.cca2.toLowerCase()}.png` });
  }

  const capital = capitalName(country);
  if (capital) {
    const capitalWrong = wrongFrom(countries.filter((item) => item.region === country.region).map(capitalName).filter(Boolean), capital, `capital:${country.cca2}`);
    addQuestion({ category: "Coğrafya", prompt: `${name} ülkesinin başkenti hangisidir?`, correct: capital, wrong: capitalWrong, explanation: `${name} ülkesinin başkenti ${capital} şehridir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} için doğru başkent hangisidir?`, correct: capital, wrong: capitalWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${capital} hangi ülkenin başkentidir?`, correct: name, wrong: flagWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `Başkenti ${capital} olan ülke hangisidir?`, correct: name, wrong: flagWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} denince hangi başkent eşleştirmesi doğrudur?`, correct: capital, wrong: capitalWrong, explanation: `${name} ile ${capital} doğru ülke-başkent eşleşmesidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `Aşağıdaki şehirlerden hangisi ${name} ülkesinin yönetim merkezidir?`, correct: capital, wrong: capitalWrong, explanation: `${name} ülkesinin başkenti ${capital} şehridir.`, factKey: `capital:${country.cca2}` });
  }

  const continentWrong = wrongFrom(continents, continent, `continent:${country.cca2}`);
  for (const prompt of [
    `${name} hangi kıtadadır?`,
    `${name} ülkesinin bulunduğu kıta hangisidir?`,
    `${name} hangi kıta ile doğru eşleştirilir?`,
    `Haritada ${name} ülkesini ararken hangi kıtaya bakmalısınız?`,
  ]) {
    addQuestion({ category: "Coğrafya", prompt, correct: continent, wrong: continentWrong, explanation: `${name}, ${continent} kıtasında yer alır.`, factKey: `continent:${country.cca2}` });
  }

  const codeWrong = wrongFrom(countries.map((item) => item.cca2), country.cca2, `code:${country.cca2}`);
  addQuestion({ category: "Coğrafya", prompt: `${name} ülkesinin iki harfli uluslararası kodu hangisidir?`, correct: country.cca2, wrong: codeWrong, explanation: `${name} için ISO 3166-1 alpha-2 kodu ${country.cca2}'dir.`, factKey: `code:${country.cca2}` });
  addQuestion({ category: "Coğrafya", prompt: `${country.cca2} ülke kodu hangi ülkeye aittir?`, correct: name, wrong: flagWrong, explanation: `${country.cca2}, ${name} ülkesinin iki harfli kodudur.`, factKey: `code:${country.cca2}` });
  addQuestion({ category: "Coğrafya", prompt: `ISO sisteminde ${name} için kullanılan iki harf hangileridir?`, correct: country.cca2, wrong: codeWrong, explanation: `${name} için iki harfli ISO kodu ${country.cca2}'dir.`, factKey: `code:${country.cca2}` });
  addQuestion({ category: "Coğrafya", prompt: `İki harfli kodu ${country.cca2} olan ülke hangisidir?`, correct: name, wrong: flagWrong, explanation: `${country.cca2} kodu ${name} ülkesini belirtir.`, factKey: `code:${country.cca2}` });

  const alpha3Wrong = wrongFrom(countries.map((item) => item.cca3), country.cca3, `alpha3:${country.cca2}`);
  for (const prompt of [
    `${name} ülkesinin üç harfli ISO kodu hangisidir?`,
    `${name} için doğru ISO alpha-3 kodu hangisidir?`,
  ]) {
    addQuestion({ category: "Coğrafya", prompt, correct: country.cca3, wrong: alpha3Wrong, explanation: `${name} ülkesinin üç harfli ISO kodu ${country.cca3}'dir.`, factKey: `alpha3:${country.cca2}` });
  }
  for (const prompt of [
    `${country.cca3} üç harfli kodu hangi ülkeye aittir?`,
    `ISO alpha-3 kodu ${country.cca3} olan ülke hangisidir?`,
  ]) {
    addQuestion({ category: "Coğrafya", prompt, correct: name, wrong: flagWrong, explanation: `${country.cca3}, ${name} ülkesinin üç harfli kodudur.`, factKey: `alpha3:${country.cca2}` });
  }

  if (country.ccn3) {
    const numericWrong = wrongFrom(countries.map((item) => item.ccn3).filter(Boolean), country.ccn3, `numeric:${country.cca2}`);
    for (const prompt of [
      `${name} ülkesinin üç haneli sayısal ISO kodu hangisidir?`,
      `${name} için doğru sayısal ülke kodunu seçin.`,
      `${country.ccn3} sayısal ISO kodu hangi ülkeye aittir?`,
    ]) {
      const reverse = prompt.startsWith(country.ccn3);
      addQuestion({ category: "Coğrafya", prompt, correct: reverse ? name : country.ccn3, wrong: reverse ? flagWrong : numericWrong, explanation: `${name} ülkesinin sayısal ISO kodu ${country.ccn3}'tür.`, factKey: `numeric:${country.cca2}` });
    }
  }

  const official = officialCountryName(country);
  const officialWrong = wrongFrom(officialNames, official, `official:${country.cca2}`);
  for (const prompt of [`${name} ülkesinin resmî adı hangisidir?`, `${name} için doğru resmî devlet adını seçin.`]) {
    addQuestion({ category: "Coğrafya", prompt, correct: official, wrong: officialWrong, explanation: `${name} ülkesinin resmî adı “${official}” şeklindedir.`, factKey: `official:${country.cca2}` });
  }

  const currency = Object.keys(country.currencies || {})[0];
  if (currency) {
    const wrong = wrongFrom(currencyCodes, currency, `currency:${country.cca2}`);
    for (const prompt of [
      `${name} ülkesinde kullanılan para biriminin kodu hangisidir?`,
      `${name} için doğru para birimi kodu hangisidir?`,
      `${name} ülkesinde ödeme yaparken hangi para birimi koduyla karşılaşırsınız?`,
      `Aşağıdaki para birimi kodlarından hangisi ${name} ile eşleşir?`,
    ]) {
      addQuestion({ category: "Coğrafya", prompt, correct: currency, wrong, explanation: `${name} ülkesinde kullanılan para biriminin uluslararası kodu ${currency}'dir.`, factKey: `currency:${country.cca2}` });
    }
  }

  const calling = country.idd?.root && country.idd?.suffixes?.length === 1 ? `${country.idd.root}${country.idd.suffixes[0]}` : null;
  if (calling) {
    const wrong = wrongFrom(callingCodes, calling, `calling:${country.cca2}`);
    for (const prompt of [
      `${name} ülkesinin uluslararası telefon kodu hangisidir?`,
      `Hangi telefon kodu ${name} ülkesine aittir?`,
      `${name} ülkesini ararken numaranın başına hangi kod eklenir?`,
      `${calling} telefon kodu hangi ülkeye aittir?`,
    ]) {
      const reverse = prompt.startsWith(calling);
      addQuestion({ category: "Coğrafya", prompt, correct: reverse ? name : calling, wrong: reverse ? flagWrong : wrong, explanation: `${name} ülkesinin uluslararası telefon kodu ${calling}'dir.`, factKey: `calling:${country.cca2}` });
    }
  }

  const tld = country.tld?.[0];
  if (tld) {
    const wrong = wrongFrom(tlds, tld, `tld:${country.cca2}`);
    for (const prompt of [
      `${name} ülkesinin internet alan adı uzantısı hangisidir?`,
      `Hangi alan adı uzantısı ${name} ülkesine aittir?`,
      `${tld} uzantılı bir internet adresi hangi ülkeyle ilişkilidir?`,
      `Ülke kodlu alan adı ${tld} olan ülke hangisidir?`,
    ]) {
      const reverse = prompt.startsWith(tld) || prompt.includes(`adı ${tld}`);
      addQuestion({ category: "Teknoloji", prompt, correct: reverse ? name : tld, wrong: reverse ? flagWrong : wrong, explanation: `${tld}, ${name} için ayrılan ülke kodlu internet alanıdır.`, factKey: `tld:${country.cca2}` });
    }
  }

  for (const borderCode of country.borders || []) {
    const border = countryByCode.get(borderCode);
    if (!border) continue;
    const correct = countryName(border);
    const forbidden = (country.borders || []).map((code) => countryByCode.get(code)).filter(Boolean).map(countryName);
    const wrong = wrongFrom(regionalCountries, correct, `border:${country.cca2}:${borderCode}`, forbidden);
    if (wrong.length === 3) {
      for (const prompt of [
        `Hangisi ${name} ile kara sınırı olan ülkelerden biridir?`,
        `${name} ülkesinin kara komşularından biri hangisidir?`,
      ]) {
        addQuestion({ category: "Coğrafya", prompt, correct, wrong, explanation: `${name} ile ${correct} arasında kara sınırı bulunur.`, factKey: `border:${country.cca2}:${borderCode}` });
      }
    }
  }
}

for (const element of elements) {
  const name = elementNames[element.number - 1];
  const nearby = elements.filter((item) => Math.abs(item.number - element.number) <= 12 && item.number !== element.number);
  const wrongNames = wrongFrom(nearby.map((item) => elementNames[item.number - 1]), name, `element-name:${element.number}`);
  const wrongSymbols = wrongFrom(nearby.map((item) => item.symbol), element.symbol, `element-symbol:${element.number}`);
  const wrongNumbers = wrongFrom(nearby.map((item) => String(item.number)), String(element.number), `element-number:${element.number}`);
  addQuestion({ category: "Bilim", prompt: `${name} elementinin kimyasal simgesi hangisidir?`, correct: element.symbol, wrong: wrongSymbols, explanation: `${name} elementinin simgesi ${element.symbol}'dir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${element.symbol} simgesi hangi elemente aittir?`, correct: name, wrong: wrongNames, explanation: `${element.symbol}, ${name} elementinin simgesidir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Periyodik tabloda ${name} hangi simgeyle gösterilir?`, correct: element.symbol, wrong: wrongSymbols, explanation: `${name} elementinin simgesi ${element.symbol}'dir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Kimyasal simgesi ${element.symbol} olan element hangisidir?`, correct: name, wrong: wrongNames, explanation: `${element.symbol}, ${name} elementinin simgesidir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${name} elementinin atom numarası kaçtır?`, correct: String(element.number), wrong: wrongNumbers, explanation: `${name} elementinin atom numarası ${element.number}'dir.`, factKey: `element-number:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Atom numarası ${element.number} olan element hangisidir?`, correct: name, wrong: wrongNames, explanation: `${element.number} atom numarası ${name} elementine aittir.`, factKey: `element-number:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Periyodik tabloda ${name} kaç numaralı elementtir?`, correct: String(element.number), wrong: wrongNumbers, explanation: `${name} elementinin atom numarası ${element.number}'dir.`, factKey: `element-number:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${element.number}. elementin adı nedir?`, correct: name, wrong: wrongNames, explanation: `${element.number}. element ${name}'dir.`, factKey: `element-number:${element.number}` });
  const period = String(element.period);
  addQuestion({ category: "Bilim", prompt: `${name} periyodik tablonun kaçıncı periyodundadır?`, correct: period, wrong: wrongFrom(["1", "2", "3", "4", "5", "6", "7"], period, `element-period:${element.number}`), explanation: `${name}, periyodik tablonun ${period}. periyodunda bulunur.`, factKey: `element-period:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${name} hangi periyotta yer alır?`, correct: period, wrong: wrongFrom(["1", "2", "3", "4", "5", "6", "7"], period, `element-period-2:${element.number}`), explanation: `${name}, periyodik tablonun ${period}. periyodundadır.`, factKey: `element-period:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Aşağıdakilerden hangisi periyodik tablonun ${period}. periyodunda bulunur?`, correct: name, wrong: wrongFrom(elements.filter((item) => item.period !== element.period).map((item) => elementNames[item.number - 1]), name, `element-period-reverse:${element.number}`), explanation: `${name}, periyodik tablonun ${period}. periyodunda bulunur.`, factKey: `element-period:${element.number}` });

  const group = String(element.group);
  const groupWrong = wrongFrom(Array.from({ length: 18 }, (_, index) => String(index + 1)), group, `element-group:${element.number}`);
  addQuestion({ category: "Bilim", prompt: `${name} periyodik tablonun kaçıncı grubundadır?`, correct: group, wrong: groupWrong, explanation: `${name}, periyodik tablonun ${group}. grubunda yer alır.`, factKey: `element-group:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${name} için doğru periyodik tablo grubu hangisidir?`, correct: group, wrong: groupWrong, explanation: `${name}, ${group}. grup elementidir.`, factKey: `element-group:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Aşağıdakilerden hangisi periyodik tablonun ${group}. grubunda yer alır?`, correct: name, wrong: wrongFrom(elements.filter((item) => item.group !== element.group).map((item) => elementNames[item.number - 1]), name, `element-group-reverse:${element.number}`), explanation: `${name}, ${group}. grupta bulunur.`, factKey: `element-group:${element.number}` });

  const block = `${element.block} bloku`;
  const blockOptions = ["s bloku", "p bloku", "d bloku", "f bloku"];
  for (const prompt of [`${name} hangi elektron blokunda yer alır?`, `${name} için doğru periyodik tablo bloku hangisidir?`]) {
    addQuestion({ category: "Bilim", prompt, correct: block, wrong: wrongFrom(blockOptions, block, `element-block:${element.number}`), explanation: `${name}, periyodik tablonun ${block} içinde yer alır.`, factKey: `element-block:${element.number}` });
  }

  const phase = phaseNames[element.phase];
  const phaseOptions = ["Katı", "Sıvı", "Gaz", "Plazma"];
  for (const prompt of [`${name} oda koşullarında hangi hâldedir?`, `Oda koşullarında ${name} için doğru fiziksel hâl hangisidir?`]) {
    addQuestion({ category: "Bilim", prompt, correct: phase, wrong: wrongFrom(phaseOptions, phase, `element-phase:${element.number}`), explanation: `${name}, oda koşullarında ${phase.toLocaleLowerCase("tr-TR")} hâldedir.`, factKey: `element-phase:${element.number}` });
  }

  const elementCategory = elementCategoryNames[element.category];
  const categoryOptions = [...new Set(Object.values(elementCategoryNames))];
  for (const prompt of [`${name} hangi element sınıfında değerlendirilir?`, `${name} için doğru kimyasal sınıflandırma hangisidir?`]) {
    addQuestion({ category: "Bilim", prompt, correct: elementCategory, wrong: wrongFrom(categoryOptions, elementCategory, `element-category:${element.number}`), explanation: `${name}, “${elementCategory}” sınıfında değerlendirilir.`, factKey: `element-category:${element.number}` });
  }
}

for (const item of manualQuestions) addQuestion(item);

fs.writeFileSync(path.join(root, "data/manual_questions.json"), `${JSON.stringify(manualQuestions, null, 2)}\n`);

const schemaPath = path.join(root, "app/schemas/com.bilgec.app.data.BilGecDatabase/1.json");
if (!fs.existsSync(schemaPath)) throw new Error("Önce ./gradlew compileDebugKotlin çalıştırılarak Room şeması üretilmeli.");
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));
const SQL = await initSqlJs({ locateFile: (file) => path.join(root, "node_modules/sql.js/dist", file) });
const database = new SQL.Database();
for (const entity of schema.database.entities) database.run(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
for (const entity of schema.database.entities) for (const index of entity.indices || []) database.run(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
for (const query of schema.database.setupQueries || []) database.run(query);

database.run("BEGIN TRANSACTION");
const insert = database.prepare("INSERT INTO questions (id, category, prompt, option_0, option_1, option_2, option_3, correct_index, explanation, fact_key, image_asset, reviewed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
for (const question of questions) insert.run([question.id, question.category, question.prompt, ...question.options, question.correctIndex, question.explanation, question.factKey, question.imageAsset, question.reviewed ? 1 : 0]);
insert.free();
database.run("COMMIT");

const assetDirectory = path.join(root, "app/src/main/assets/database");
fs.mkdirSync(assetDirectory, { recursive: true });
fs.writeFileSync(path.join(assetDirectory, "bilgec.db"), Buffer.from(database.export()));
database.close();

const flagDirectory = path.join(root, "app/src/main/assets/flags");
fs.mkdirSync(flagDirectory, { recursive: true });
for (const country of countries) {
  const code = country.cca2.toLowerCase();
  const output = path.join(flagDirectory, `${code}.png`);
  if (fs.existsSync(output)) continue;
  const response = await fetch(`https://raw.githubusercontent.com/lipis/flag-icons/main/flags/4x3/${code}.svg`);
  if (!response.ok) throw new Error(`${code} bayrağı indirilemedi: ${response.status}`);
  await sharp(Buffer.from(await response.arrayBuffer())).resize(320, 240, { fit: "contain" }).png().toFile(output);
}

console.log(`${questions.length} soru, ${countries.length} bayrak ve SQLite veritabanı üretildi.`);
