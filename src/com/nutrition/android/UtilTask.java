package com.nutrition.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

/**
 * 
 * @author Issame
 *
 */
public class UtilTask extends AsyncTask<String, String, Nutrition> {

	@Override
	protected Nutrition doInBackground(String... params) {
		Nutrition nutrition_elements = null;
		try {
			String response = UtilTask.getJsonFromBarCode(params[0]);
			nutrition_elements = UtilTask.parseJson(response);
			return nutrition_elements;
		} catch (IOException e) {
			e.printStackTrace();
			nutrition_elements = new Nutrition("IOException");
		} catch (ParseException e) {
			e.printStackTrace();
			nutrition_elements = new Nutrition("ParseException");
		}
		
		return nutrition_elements;
	}

	public static String getJsonFromBarCode(String barcode) throws IOException {

		String url = "http://world.openfoodfacts.org/api/v0/product/" + barcode + ".json";
		StringBuffer response = new StringBuffer();
		BufferedReader in = null;
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
			String inputLine;

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new IOException();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return response.toString();
	}

	public static Nutrition parseJson(String jsonInput) throws IOException, ParseException {
		Nutrition nutrition_elements = new Nutrition("");
		JSONParser parser = new JSONParser();
		Object root = parser.parse(new StringReader(jsonInput));
		JSONObject jsonRoot = (JSONObject) root;
		Long status = (Long) jsonRoot.get("status");

		if (status.equals(Long.valueOf(1))) {
			JSONObject jsonProduct = (JSONObject) jsonRoot.get("product");
			if(jsonProduct != null){
				JSONObject nutrient_levels = (JSONObject) jsonProduct.get("nutrient_levels");
				JSONObject nutriments = (JSONObject) jsonProduct.get("nutriments");
				
				if(nutrient_levels != null && nutriments != null){
					nutrition_elements.setData_per((String) jsonProduct.get("nutrition_data_per"));
					nutrition_elements.setProduct_name((String) jsonProduct.get("product_name"));
					nutrition_elements.setFat_level((String) nutrient_levels.get("fat"));
					nutrition_elements.setFat_weight((String) nutriments.get("fat_100g") + "g");
					nutrition_elements.setSaturated_fat_level((String) nutrient_levels.get("saturated-fat"));
					nutrition_elements.setSaturated_fat_weight((String) nutriments.get("saturated-fat_100g") + "g");
					nutrition_elements.setSugars_level((String) nutrient_levels.get("sugars"));
					nutrition_elements.setSugars_weight((String) nutriments.get("sugars_100g") + "g");
					nutrition_elements.setSalt_level((String) nutrient_levels.get("salt"));
					nutrition_elements.setSalt_weight((String) nutriments.get("salt_100g") + "g");

					String energy_unit = (String) nutriments.get("energy_unit");
					String energy_100g = nutriments.get("energy_value") + "";
					nutrition_elements.setEnergy_unit(energy_unit);

					if (energy_100g != null && !"".equals(energy_100g)) {
						if ("KJ".equalsIgnoreCase(energy_unit)) {
							double energy_100g_Kcal = round(Double.valueOf(energy_100g).doubleValue() / 4.184, 2);
							nutrition_elements.setEnergy_100g(energy_100g_Kcal + "Kcal");
						} else {
							nutrition_elements.setEnergy_100g(energy_100g + "Kcal");
						}
					}
					
					URL imageUrl = new URL((String) jsonProduct.get("image_front_url"));
					Bitmap productImage = BitmapFactory.decodeStream(imageUrl.openConnection().getInputStream());
					nutrition_elements.setImage(productImage);
				} else {
					nutrition_elements.setError("EmptyProduct");
				}
			} else {
				nutrition_elements.setError("EmptyProduct");
			}

		} else {
			nutrition_elements.setError("NotFoundProduct");
		}


		return nutrition_elements;
	}

	public static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();

		long factor = (long) Math.pow(10, places);
		value = value * factor;
		long tmp = Math.round(value);
		return (double) tmp / factor;
	}

}
