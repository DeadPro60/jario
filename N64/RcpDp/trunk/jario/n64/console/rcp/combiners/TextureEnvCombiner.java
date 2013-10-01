/**
 * Copyright 2005, 2013 Jason LaDere
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Originally based on Project64 code.
 *
 */

package jario.n64.console.rcp.combiners;

import java.nio.FloatBuffer;
import javax.media.opengl.GL2;

public class TextureEnvCombiner implements Combiners.CompiledCombiner
{
	public static final int GL_SECONDARY_COLOR_ATIX = 0x8747;
	public static final int GL_TEXTURE_OUTPUT_RGB_ATIX = 0x8748;
	public static final int GL_TEXTURE_OUTPUT_ALPHA_ATIX = 0x8749;

	private static class TexEnvCombinerArg
	{
		int source, operand;

		public TexEnvCombinerArg(int source, int operand)
		{
			this.source = source;
			this.operand = operand;
		}
	};

	private class TexEnvCombinerStage
	{
		int constant;
		boolean used;
		int combine;
		TexEnvCombinerArg[] arg = new TexEnvCombinerArg[3];
		int outputTexture;

		public TexEnvCombinerStage()
		{
			for (int i = 0; i < arg.length; i++)
			{
				arg[i] = new TexEnvCombinerArg(0, 0);
			}
		}
	};

	private static TexEnvCombinerArg[] TexEnvArgs =
	{
			// CMB
			new TexEnvCombinerArg(GL2.GL_PREVIOUS, GL2.GL_SRC_COLOR),
			// T0
			new TexEnvCombinerArg(GL2.GL_TEXTURE, GL2.GL_SRC_COLOR),
			// T1
			new TexEnvCombinerArg(GL2.GL_TEXTURE, GL2.GL_SRC_COLOR),
			// PRIM
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// SHADE
			new TexEnvCombinerArg(GL2.GL_PRIMARY_COLOR, GL2.GL_SRC_COLOR),
			// ENV
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// CENTER
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// SCALE
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// CMBALPHA
			new TexEnvCombinerArg(GL2.GL_PREVIOUS, GL2.GL_SRC_ALPHA),
			// T0ALPHA
			new TexEnvCombinerArg(GL2.GL_TEXTURE, GL2.GL_SRC_ALPHA),
			// T1ALPHA
			new TexEnvCombinerArg(GL2.GL_TEXTURE, GL2.GL_SRC_ALPHA),
			// PRIMALPHA
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_ALPHA),
			// SHADEALPHA
			new TexEnvCombinerArg(GL2.GL_PRIMARY_COLOR, GL2.GL_SRC_ALPHA),
			// ENVALPHA
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// LODFRAC
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// PRIMLODFRAC
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// NOISE
			new TexEnvCombinerArg(GL2.GL_TEXTURE, GL2.GL_SRC_COLOR),
			// K4
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// K5
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// ONE
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR),
			// ZERO
			new TexEnvCombinerArg(GL2.GL_CONSTANT, GL2.GL_SRC_COLOR)
	};

	private boolean usesT0, usesT1, usesNoise;

	private int usedUnits;

	private static class Vertex
	{
		int color, secondaryColor, alpha;
	};

	private Vertex vertex = new Vertex();

	private TexEnvCombinerStage[] color = new TexEnvCombinerStage[8];
	private TexEnvCombinerStage[] alpha = new TexEnvCombinerStage[8];

	private Combiners combiner;

	// called by Combiners
	public TextureEnvCombiner(Combiners.Combiner c, Combiners.Combiner a)
	{
		for (int i = 0; i < color.length; i++)
			color[i] = new TexEnvCombinerStage();
		for (int i = 0; i < alpha.length; i++)
			alpha[i] = new TexEnvCombinerStage();

		int curUnit, combinedUnit;

		for (int i = 0; i < Combiners.maxTextureUnits; i++)
		{
			color[i].combine = GL2.GL_REPLACE;
			alpha[i].combine = GL2.GL_REPLACE;

			SetColorCombinerValues(i, 0, GL2.GL_PREVIOUS, GL2.GL_SRC_COLOR);
			SetColorCombinerValues(i, 1, GL2.GL_PREVIOUS, GL2.GL_SRC_COLOR);
			SetColorCombinerValues(i, 2, GL2.GL_PREVIOUS, GL2.GL_SRC_COLOR);
			color[i].constant = Combiners.COMBINED;
			color[i].outputTexture = GL2.GL_TEXTURE0 + i;

			SetAlphaCombinerValues(i, 0, GL2.GL_PREVIOUS, GL2.GL_SRC_ALPHA);
			SetAlphaCombinerValues(i, 1, GL2.GL_PREVIOUS, GL2.GL_SRC_ALPHA);
			SetAlphaCombinerValues(i, 2, GL2.GL_PREVIOUS, GL2.GL_SRC_ALPHA);
			alpha[i].constant = Combiners.COMBINED;
			alpha[i].outputTexture = GL2.GL_TEXTURE0 + i;
		}

		usesT0 = false;
		usesT1 = false;

		vertex.color = Combiners.COMBINED;
		vertex.secondaryColor = Combiners.COMBINED;
		vertex.alpha = Combiners.COMBINED;

		curUnit = 0;

		for (int i = 0; i < a.numStages; i++)
		{
			for (int j = 0; j < a.stage[i].numOps; j++)
			{
				float sb = 0.0f;

				if (a.stage[i].op[j].param1 == Combiners.PRIMITIVE_ALPHA)
					sb = Combiners.primColor.a;
				else if (a.stage[i].op[j].param1 == Combiners.ENV_ALPHA)
					sb = Combiners.envColor[3];
				else if (a.stage[i].op[j].param1 == Combiners.ONE)
					sb = 1.0f;

				if (((a.stage[i].numOps - j) >= 3) &&
						(a.stage[i].op[j].op == Combiners.SUB) &&
						(a.stage[i].op[j + 1].op == Combiners.MUL) &&
						(a.stage[i].op[j + 2].op == Combiners.ADD) &&
						(sb > 0.5f) &&
						(Combiners.ARB_texture_env_combine))
				{
					usesT0 |= a.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA;
					usesT1 |= a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA;

					if (a.stage[i].op[j].param1 == Combiners.ONE)
					{
						SetAlphaCombinerValues(curUnit, 0, alpha[curUnit].arg[0].source, GL2.GL_ONE_MINUS_SRC_ALPHA);
					}
					else
					{
						alpha[curUnit].combine = GL2.GL_SUBTRACT;
						SetAlphaCombinerValues(curUnit, 1, alpha[curUnit].arg[0].source, GL2.GL_SRC_ALPHA);
						SetAlphaCombinerArg(curUnit, 0, a.stage[i].op[j].param1);

						curUnit++;
					}

					j++;

					usesT0 |= a.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA;
					usesT1 |= a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA;

					alpha[curUnit].combine = GL2.GL_MODULATE;
					SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);

					curUnit++;
					j++;

					usesT0 |= a.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA;
					usesT1 |= a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA;

					alpha[curUnit].combine = GL2.GL_SUBTRACT;
					SetAlphaCombinerArg(curUnit, 0, a.stage[i].op[j].param1);

					curUnit++;
				}
				else
				{
					usesT0 |= a.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA;
					usesT1 |= a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA;

					switch (a.stage[i].op[j].op)
					{
					case Combiners.LOAD:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								(a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA) && (curUnit == 0))
							curUnit++;

						alpha[curUnit].combine = GL2.GL_REPLACE;

						SetAlphaCombinerArg(curUnit, 0, a.stage[i].op[j].param1);
						break;
					case Combiners.SUB:
						if (!Combiners.ARB_texture_env_combine)
							break;

						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								(a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA) && (curUnit == 0))
							curUnit++;

						if ((j > 0) && (a.stage[i].op[j - 1].op == Combiners.LOAD) && (a.stage[i].op[j - 1].param1 == Combiners.ONE))
						{
							SetAlphaCombinerArg(curUnit, 0, a.stage[i].op[j].param1);
							alpha[curUnit].arg[0].operand = GL2.GL_ONE_MINUS_SRC_ALPHA;
						}
						else if ((Combiners.ATI_texture_env_combine3) && (curUnit > 0) && (alpha[curUnit - 1].combine == GL2.GL_MODULATE))
						{
							curUnit--;
							SetAlphaCombinerValues(curUnit, 2, alpha[curUnit].arg[1].source, alpha[curUnit].arg[1].operand);
							// alpha[curUnit].combine = GL2.GL_MODULATE_SUBTRACT_ATI;
							alpha[curUnit].combine = 0x8746;
							SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);
							curUnit++;
						}
						else
						{
							alpha[curUnit].combine = GL2.GL_SUBTRACT;
							SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);
							curUnit++;
						}
						break;
					case Combiners.MUL:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								(a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA) && (curUnit == 0))
							curUnit++;

						alpha[curUnit].combine = GL2.GL_MODULATE;

						SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);
						curUnit++;
						break;
					case Combiners.ADD:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								(a.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA) && (curUnit == 0))
							curUnit++;

						if ((Combiners.ATI_texture_env_combine3) && (curUnit > 0) && (alpha[curUnit - 1].combine == GL2.GL_MODULATE))
						{
							curUnit--;
							SetAlphaCombinerValues(curUnit, 2, alpha[curUnit].arg[1].source, alpha[curUnit].arg[1].operand);
							// alpha[curUnit].combine = GL2.GL_MODULATE_ADD_ATI;
							alpha[curUnit].combine = 0x8744;
							SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);
						}
						else
						{
							alpha[curUnit].combine = GL2.GL_ADD;
							SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param1);
						}
						curUnit++;
						break;
					case Combiners.INTER:
						usesT0 |= (a.stage[i].op[j].param2 == Combiners.TEXEL0_ALPHA) || (a.stage[i].op[j].param3 == Combiners.TEXEL0_ALPHA);
						usesT1 |= (a.stage[i].op[j].param2 == Combiners.TEXEL1_ALPHA) || (a.stage[i].op[j].param3 == Combiners.TEXEL1_ALPHA);

						alpha[curUnit].combine = GL2.GL_INTERPOLATE;

						SetAlphaCombinerArg(curUnit, 0, a.stage[i].op[j].param1);
						SetAlphaCombinerArg(curUnit, 1, a.stage[i].op[j].param2);
						SetAlphaCombinerArg(curUnit, 2, a.stage[i].op[j].param3);

						curUnit++;
						break;
					}
				}
			}
			combinedUnit = StrictMath.max(curUnit - 1, 0);
		}

		usedUnits = StrictMath.max(curUnit, 1);

		curUnit = 0;
		for (int i = 0; i < c.numStages; i++)
		{
			for (int j = 0; j < c.stage[i].numOps; j++)
			{
				float sb = 0.0f;

				if (c.stage[i].op[j].param1 == Combiners.PRIMITIVE)
					sb = (Combiners.primColor.r + Combiners.primColor.b + Combiners.primColor.g) / 3.0f;
				else if (c.stage[i].op[j].param1 == Combiners.ENVIRONMENT)
					sb = (Combiners.envColor[0] + Combiners.envColor[1] + Combiners.envColor[2]) / 3.0f;

				// This helps with problems caused by not using signed values between texture units
				if (((c.stage[i].numOps - j) >= 3) &&
						(c.stage[i].op[j].op == Combiners.SUB) &&
						(c.stage[i].op[j + 1].op == Combiners.MUL) &&
						(c.stage[i].op[j + 2].op == Combiners.ADD) &&
						(sb > 0.5f) &&
						(Combiners.ARB_texture_env_combine))
				{
					usesT0 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL0) || (c.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA));
					usesT1 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA));

					color[curUnit].combine = GL2.GL_SUBTRACT;
					SetColorCombinerValues(curUnit, 1, color[curUnit].arg[0].source, color[curUnit].arg[0].operand);
					SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);

					curUnit++;
					j++;

					usesT0 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL0) || (c.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA));
					usesT1 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA));

					color[curUnit].combine = GL2.GL_MODULATE;
					SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);

					curUnit++;
					j++;

					usesT0 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL0) || (c.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA));
					usesT1 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA));

					color[curUnit].combine = GL2.GL_SUBTRACT;
					SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);

					curUnit++;
				}
				else
				{
					usesT0 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL0) || (c.stage[i].op[j].param1 == Combiners.TEXEL0_ALPHA));
					usesT1 |= ((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA));

					switch (c.stage[i].op[j].op)
					{
					case Combiners.LOAD:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA)) && (curUnit == 0))
							curUnit++;

						color[curUnit].combine = GL2.GL_REPLACE;

						SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);
						break;
					case Combiners.SUB:
						if (!Combiners.ARB_texture_env_combine)
							break;

						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA)) && (curUnit == 0))
							curUnit++;

						if ((j > 0) && (c.stage[i].op[j - 1].op == Combiners.LOAD) && (c.stage[i].op[j - 1].param1 == Combiners.ONE))
						{
							SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);
							color[curUnit].arg[0].operand = GL2.GL_ONE_MINUS_SRC_COLOR;
						}
						else if ((Combiners.ATI_texture_env_combine3) && (curUnit > 0) && (color[curUnit - 1].combine == GL2.GL_MODULATE))
						{
							curUnit--;
							SetColorCombinerValues(curUnit, 2, color[curUnit].arg[1].source, color[curUnit].arg[1].operand);
							// color[curUnit].combine = GL2.GL_MODULATE_SUBTRACT_ATI;
							color[curUnit].combine = 0x8746;
							SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);
							curUnit++;
						}
						else
						{
							color[curUnit].combine = GL2.GL_SUBTRACT;
							SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);
							curUnit++;
						}
						break;
					case Combiners.MUL:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA)) && (curUnit == 0))
							curUnit++;

						color[curUnit].combine = GL2.GL_MODULATE;

						SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);
						curUnit++;
						break;
					case Combiners.ADD:
						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param1 == Combiners.TEXEL1_ALPHA)) && (curUnit == 0))
							curUnit++;

						// ATI_texture_env_combine3 adds GL_MODULATE_ADD_ATI; saves texture units
						if ((Combiners.ATI_texture_env_combine3) && (curUnit > 0) && (color[curUnit - 1].combine == GL2.GL_MODULATE))
						{
							curUnit--;
							SetColorCombinerValues(curUnit, 2, color[curUnit].arg[1].source, color[curUnit].arg[1].operand);
							// color[curUnit].combine = GL2.GL_MODULATE_ADD_ATI;
							color[curUnit].combine = 0x8744;
							SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);
						}
						else
						{
							color[curUnit].combine = GL2.GL_ADD;
							SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param1);
						}
						curUnit++;
						break;
					case Combiners.INTER:
						usesT0 |= (c.stage[i].op[j].param2 == Combiners.TEXEL0) || (c.stage[i].op[j].param3 == Combiners.TEXEL0) || (c.stage[i].op[j].param3 == Combiners.TEXEL0_ALPHA);
						usesT1 |= (c.stage[i].op[j].param2 == Combiners.TEXEL1) || (c.stage[i].op[j].param3 == Combiners.TEXEL1) || (c.stage[i].op[j].param3 == Combiners.TEXEL1_ALPHA);

						if (!(Combiners.ARB_texture_env_crossbar || Combiners.NV_texture_env_combine4) &&
								((c.stage[i].op[j].param1 == Combiners.TEXEL1) || (c.stage[i].op[j].param2 == Combiners.TEXEL1) || (c.stage[i].op[j].param3 == Combiners.TEXEL1) || (c.stage[i].op[j].param3 == Combiners.TEXEL1_ALPHA)) && (curUnit == 0))
						{
							if (c.stage[i].op[j].param1 == Combiners.TEXEL0)
							{
								color[curUnit].combine = GL2.GL_REPLACE;
								SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);
								c.stage[i].op[j].param1 = Combiners.COMBINED;
							}
							if (c.stage[i].op[j].param2 == Combiners.TEXEL0)
							{
								color[curUnit].combine = GL2.GL_REPLACE;
								SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param2);

								c.stage[i].op[j].param2 = Combiners.COMBINED;
							}
							if (c.stage[i].op[j].param3 == Combiners.TEXEL0)
							{
								color[curUnit].combine = GL2.GL_REPLACE;
								SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param3);
								c.stage[i].op[j].param3 = Combiners.COMBINED;
							}
							if (c.stage[i].op[j].param3 == Combiners.TEXEL0_ALPHA)
							{
								color[curUnit].combine = GL2.GL_REPLACE;
								SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param3);
								c.stage[i].op[j].param3 = Combiners.COMBINED_ALPHA;
							}

							curUnit++;
						}

						color[curUnit].combine = GL2.GL_INTERPOLATE;

						SetColorCombinerArg(curUnit, 0, c.stage[i].op[j].param1);
						SetColorCombinerArg(curUnit, 1, c.stage[i].op[j].param2);
						SetColorCombinerArg(curUnit, 2, c.stage[i].op[j].param3);

						curUnit++;
						break;
					}
				}
			}
			combinedUnit = StrictMath.max(curUnit - 1, 0);
		}

		usedUnits = StrictMath.max(curUnit, usedUnits);
	}

	// called by Combiners
	public static void init()
	{
		// int[] tex = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

		// for (int i = 0; i < OpenGl.maxTextureUnits; i++) {
		// OpenGl.cache.activateDummy(i);
		// }

		if ((Combiners.ARB_texture_env_crossbar) || (Combiners.NV_texture_env_combine4) || (Combiners.ATIX_texture_env_route))
		{
			TexEnvArgs[Combiners.TEXEL0].source = GL2.GL_TEXTURE0;
			TexEnvArgs[Combiners.TEXEL0_ALPHA].source = GL2.GL_TEXTURE0;

			TexEnvArgs[Combiners.TEXEL1].source = GL2.GL_TEXTURE1;
			TexEnvArgs[Combiners.TEXEL1_ALPHA].source = GL2.GL_TEXTURE1;
		}

		if (Combiners.ATI_texture_env_combine3)
		{
			TexEnvArgs[Combiners.ONE].source = GL2.GL_ONE;
			TexEnvArgs[Combiners.ZERO].source = GL2.GL_ZERO;
		}
	}

	// called by Combiners
	public static void BeginTextureUpdate_texture_env_combine(GL2 gl)
	{
		for (int i = 0; i < Combiners.maxTextureUnits; i++)
		{
			gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
			gl.glDisable(GL2.GL_TEXTURE_2D);
		}
	}

	// called by Combiners
	public void set(GL2 gl, Combiners combiner)
	{
		combiner.usesT0 = usesT0;
		combiner.usesT1 = usesT1;
		combiner.usesNoise = false;
		combiner.vertex.color = vertex.color;
		combiner.vertex.secondaryColor = vertex.secondaryColor;
		combiner.vertex.alpha = vertex.alpha;
		this.combiner = combiner;

		for (int i = 0; i < Combiners.maxTextureUnits; i++)
		{
			gl.glActiveTexture(GL2.GL_TEXTURE0 + i);

			if ((i < usedUnits) || ((i < 2) && usesT1))
			{
				gl.glEnable(GL2.GL_TEXTURE_2D);

				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);

				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, color[i].combine);

				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE0_RGB, color[i].arg[0].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, color[i].arg[0].operand);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_RGB, color[i].arg[1].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND1_RGB, color[i].arg[1].operand);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_RGB, color[i].arg[2].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND2_RGB, color[i].arg[2].operand);
				if (Combiners.ATIX_texture_env_route)
					gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL_TEXTURE_OUTPUT_RGB_ATIX, color[i].outputTexture);

				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, alpha[i].combine);

				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE0_ALPHA, alpha[i].arg[0].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_ALPHA, alpha[i].arg[0].operand);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE1_ALPHA, alpha[i].arg[1].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND1_ALPHA, alpha[i].arg[1].operand);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SOURCE2_ALPHA, alpha[i].arg[2].source);
				gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND2_ALPHA, alpha[i].arg[2].operand);
				if (Combiners.ATIX_texture_env_route)
					gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL_TEXTURE_OUTPUT_ALPHA_ATIX, alpha[i].outputTexture);
			}
			else
			{
				gl.glDisable(GL2.GL_TEXTURE_2D);
			}
		}
	}

	// called by Combiners
	public void updateColors(GL2 gl)
	{
		FloatBuffer c = FloatBuffer.allocate(4);

		for (int i = 0; i < Combiners.maxTextureUnits; i++)
		{
			combiner.setConstant(c, color[i].constant, alpha[i].constant);
			gl.glActiveTexture(GL2.GL_TEXTURE0 + i);
			gl.glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, c.array(), 0);
		}
	}

	// Private Methods /////////////////////////////////////////////////////////

	private void SetColorCombinerArg(int n, int a, int i)
	{
		if (TexEnvArgs[i].source == GL2.GL_CONSTANT)
		{
			if ((i > 5) && ((alpha[n].constant == Combiners.COMBINED) || (alpha[n].constant == i)))
			{
				alpha[n].constant = i;
				color[n].arg[a].source = GL2.GL_CONSTANT;
				color[n].arg[a].operand = GL2.GL_SRC_ALPHA;
			}
			else if ((i > 5) && ((vertex.alpha == Combiners.COMBINED) || (vertex.alpha == i)))
			{
				vertex.alpha = i;
				color[n].arg[a].source = GL2.GL_PRIMARY_COLOR;
				color[n].arg[a].operand = GL2.GL_SRC_ALPHA;
			}
			else if ((color[n].constant == Combiners.COMBINED) || (color[n].constant == i))
			{
				color[n].constant = i;
				color[n].arg[a].source = GL2.GL_CONSTANT;
				color[n].arg[a].operand = GL2.GL_SRC_COLOR;
			}
			else if (Combiners.ATIX_texture_env_route && ((vertex.secondaryColor == Combiners.COMBINED) || (vertex.secondaryColor == i)))
			{
				vertex.secondaryColor = i;
				color[n].arg[a].source = GL_SECONDARY_COLOR_ATIX;
				color[n].arg[a].operand = GL2.GL_SRC_COLOR;
			}
			else if ((vertex.color == Combiners.COMBINED) || (vertex.color == i))
			{
				vertex.color = i;
				color[n].arg[a].source = GL2.GL_PRIMARY_COLOR;
				color[n].arg[a].operand = GL2.GL_SRC_COLOR;
			}
		}
		else
		{
			color[n].arg[a].source = TexEnvArgs[i].source;
			color[n].arg[a].operand = TexEnvArgs[i].operand;
		}
	}

	private void SetColorCombinerValues(int n, int a, int s, int o)
	{
		color[n].arg[a].source = s;
		color[n].arg[a].operand = o;
	}

	private void SetAlphaCombinerArg(int n, int a, int i)
	{
		if (TexEnvArgs[i].source == GL2.GL_CONSTANT)
		{
			if ((alpha[n].constant == Combiners.COMBINED) || (alpha[n].constant == i))
			{
				alpha[n].constant = i;
				alpha[n].arg[a].source = GL2.GL_CONSTANT;
				alpha[n].arg[a].operand = GL2.GL_SRC_ALPHA;
			}
			else if ((vertex.alpha == Combiners.COMBINED) || (vertex.alpha == i))
			{
				vertex.alpha = i;
				alpha[n].arg[a].source = GL2.GL_PRIMARY_COLOR;
				alpha[n].arg[a].operand = GL2.GL_SRC_ALPHA;
			}
		}
		else
		{
			alpha[n].arg[a].source = TexEnvArgs[i].source;
			alpha[n].arg[a].operand = GL2.GL_SRC_ALPHA;
		}
	}

	private void SetAlphaCombinerValues(int n, int a, int s, int o)
	{
		alpha[n].arg[a].source = s;
		alpha[n].arg[a].operand = o;
	}
}
